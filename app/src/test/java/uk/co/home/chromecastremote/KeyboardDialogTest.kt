package uk.co.home.chromecastremote

import android.app.Activity
import android.app.AlertDialog
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowAlertDialog
import uk.co.home.chromecastremote.protocol.RemoteProtocol
import uk.co.home.chromecastremote.protocol.TvTextTarget
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 35])
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@LooperMode(LooperMode.Mode.PAUSED)
class KeyboardDialogTest {
    private class FakeRemote : KeyboardRemote {
        val edits = mutableListOf<Pair<Long, String>>()
        val actions = mutableListOf<String>()
        var update: ((RemoteState) -> Unit)? = null
        var completeImmediately = true
        var completion: (() -> Unit)? = null
        override var state = RemoteState("tv", true, "Connected", sessionId = 1, features = RemoteProtocol.IME,
            textTarget = TvTextTarget(17, 9, 1, 3, 3, 3, 3, "old"))
        override fun keyboard(enabled: Boolean) = Unit
        override fun replaceText(sessionId: Long, target: TvTextTarget, text: String, selection: Int, done: (Boolean, String) -> Unit) {
            edits.add(sessionId to text); actions.add("text:$text")
            state = state.copy(textTarget = target.copy(text = text, length = text.length, start = selection, end = selection))
            update?.invoke(state)
            val finish = { done(true, "Sent") }
            if (completeImmediately) finish() else completion = finish
        }
        override fun submitText(sessionId: Long, target: TvTextTarget, done: (Boolean, String) -> Unit) {
            actions.add("enter"); done(true, "Sent")
        }
    }
    private fun findInput(view: View): EditText? {
        if (view is EditText) return view
        if (view is ViewGroup) for (index in 0 until view.childCount) findInput(view.getChildAt(index))?.let { return it }
        return null
    }
    private fun exercise(test: (FakeRemote, TvKeyboardDialog, EditText, AlertDialog) -> Unit) {
        val activity = Robolectric.buildActivity(Activity::class.java).setup()
        val remote = FakeRemote()
        val panel = TvKeyboardDialog(activity.get(), TvDevice(id = "tv", name = "Guest Room", host = "192.168.1.20"), remote) {}
        remote.update = panel::update
        try {
            panel.show()
            val dialog = ShadowAlertDialog.getLatestAlertDialog()
            test(remote, panel, findInput(dialog.window!!.decorView)!!, dialog)
        } finally { panel.close(); shadowOf(Looper.getMainLooper()).idle(); activity.pause().stop().destroy() }
    }
    @Test fun typingAndBackspaceSendAutomaticallyAndCoalesceRapidChanges() = exercise { remote, _, input, _ ->
        assertEquals("old", input.text.toString()); assertTrue(remote.edits.isEmpty())
        input.setText("first"); input.setText("final"); input.setSelection(5)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(200))
        assertEquals(listOf(1L to "final"), remote.edits)
        input.text.delete(4, 5)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(200))
        assertEquals("fina", remote.edits.last().second)
        input.text.clear()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(200))
        assertEquals("", remote.edits.last().second)
    }
    @Test fun changingFieldsRebindsAutomaticallyWithoutSendingTheOldDraft() = exercise { remote, panel, input, _ ->
        input.setText("unsent draft")
        remote.state = remote.state.copy(textTarget = TvTextTarget(18, 1, 2, 3, 3, 3, 3, "new"))
        panel.update(remote.state)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(300))
        assertTrue(remote.edits.isEmpty()); assertEquals("new", input.text.toString())
        input.text.append(" text")
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(200))
        assertEquals("new text", remote.edits.single().second)
    }
    @Test fun reconnectDoesNotReplayTheOldDraftAndClosingClearsTextImmediately() = exercise { remote, panel, input, _ ->
        input.setText("unsent")
        remote.state = remote.state.copy(sessionId = 2, textTarget = TvTextTarget(17, 9, 1, 0, 0, 0, 3))
        panel.update(remote.state)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(300))
        assertTrue(remote.edits.isEmpty()); assertEquals("", input.text.toString())
        input.setText("clear on close"); panel.close()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(300))
        assertTrue(input.text.isEmpty()); assertTrue(remote.edits.isEmpty())
    }
    @Test fun enterWaitsForTheLatestTextBeforeSubmitting() = exercise { remote, _, input, dialog ->
        remote.completeImmediately = false
        input.setText("first")
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(200))
        input.setText("latest")
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        remote.completion!!.invoke()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(listOf("text:first", "text:latest"), remote.actions)
        remote.completion!!.invoke()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(listOf("text:first", "text:latest", "enter"), remote.actions)
    }
}
