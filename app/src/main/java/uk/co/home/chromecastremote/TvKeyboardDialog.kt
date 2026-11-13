package uk.co.home.chromecastremote

import android.app.Activity
import android.app.AlertDialog
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import uk.co.home.chromecastremote.protocol.RemoteProtocol
import uk.co.home.chromecastremote.protocol.TvKeyboard
import uk.co.home.chromecastremote.protocol.TvTextTarget

/** Live edits are coalesced and bound to one TV/session/focus. Drafts never survive a rebind. */
class TvKeyboardDialog(
    private val activity: Activity, private val device: TvDevice, private val controller: KeyboardRemote,
    private val keepKeyboardEnabled: () -> Boolean = { true }, private val dismissed: () -> Unit
) {
    private val handler = Handler(Looper.getMainLooper())
    private val status = TextView(activity).apply { textSize = 15f }
    private val input = EditText(activity).apply {
        hint = "Type on your TV"; contentDescription = "Keyboard for ${device.name}"
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        imeOptions = EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING or EditorInfo.IME_ACTION_SEARCH
        filters = arrayOf(InputFilter.LengthFilter(TvKeyboard.MAX_TEXT))
        isSaveEnabled = false; importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        isEnabled = false
    }
    private var session = 0L
    private var target: TvTextTarget? = null
    private var epoch = 0L
    private var syncing = false
    private var dirty = false
    private var busy = false
    private var submitPending = false
    private var closed = false
    var lastBinding: String? = null; private set
    private val flush = Runnable { sendPending() }
    private val dialog: AlertDialog
    init {
        val spacing = (20 * activity.resources.displayMetrics.density).toInt()
        val form = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL; setPadding(spacing, spacing / 2, spacing, spacing / 2)
            addView(status); addView(input, LinearLayout.LayoutParams(-1, -2))
        }
        dialog = AlertDialog.Builder(activity).setTitle("Keyboard · ${device.name}")
            .setView(ScrollView(activity).apply { addView(form) })
            .setPositiveButton(R.string.enter_on_tv, null).setNegativeButton("Done", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { requestSubmit() }
            update(controller.state)
        }
        dialog.setOnDismissListener {
            discard(); closed = true
            controller.keyboard(keepKeyboardEnabled()); dismissed()
        }
        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                if (!syncing && !closed && target != null) {
                    dirty = true; handler.removeCallbacks(flush); handler.postDelayed(flush, EDIT_DELAY_MS)
                }
            }
        })
        input.setOnEditorActionListener { _, action, event ->
            if (action != EditorInfo.IME_ACTION_NONE || event?.keyCode == android.view.KeyEvent.KEYCODE_ENTER) {
                if (event == null || event.action == android.view.KeyEvent.ACTION_DOWN) requestSubmit()
                true
            } else false
        }
    }
    @Suppress("DEPRECATION")
    fun show() {
        dialog.show(); dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        controller.keyboard(true); update(controller.state)
    }
    fun close() { if (!closed) { discard(); closed = true; dialog.dismiss() } }
    private fun discard() {
        epoch++; handler.removeCallbacksAndMessages(null)
        target = null; dirty = false; busy = false; submitPending = false
        syncing = true; input.text.clear(); syncing = false; input.isEnabled = false
    }
    fun update(state: RemoteState) {
        if (closed) return
        if (state.deviceId != device.id) { close(); return }
        val focused = state.textTarget
        if (!state.connected || focused == null || state.features and RemoteProtocol.IME == 0) {
            val hadFocus = target != null
            discard(); session = 0
            status.setText(when {
                !state.connected -> R.string.keyboard_connecting
                state.features and RemoteProtocol.IME == 0 -> R.string.keyboard_unsupported
                else -> R.string.keyboard_no_field
            })
            if (hadFocus && state.connected && focused == null) { close(); return }
            refreshButtons(); return
        }
        val changedFocus = session != state.sessionId || target?.fieldId != focused.fieldId || target?.focusVersion != focused.focusVersion
        if (changedFocus) {
            discard(); session = state.sessionId
            input.inputType = focused.inputType.takeIf { (it and InputType.TYPE_MASK_CLASS) in 1..3 } ?: InputType.TYPE_CLASS_TEXT
            val action = (focused.imeOptions and EditorInfo.IME_MASK_ACTION).takeIf { it in 2..7 } ?: EditorInfo.IME_ACTION_DONE
            input.imeOptions = EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING or action
            syncInput(focused); target = focused
            lastBinding = "${device.id}:$session:${focused.fieldId}:${focused.focusVersion}"
            input.isEnabled = true; input.requestFocus()
            handler.post { if (!closed && target != null) activity.getSystemService(InputMethodManager::class.java).showSoftInput(input, InputMethodManager.SHOW_IMPLICIT) }
        } else {
            target = focused
            if (!dirty && !busy && input.text.toString() != focused.text) syncInput(focused)
        }
        if (!busy) status.setText(R.string.keyboard_live_ready)
        refreshButtons()
    }
    private fun syncInput(focused: TvTextTarget) {
        syncing = true; input.setText(focused.text)
        input.setSelection(focused.start.coerceIn(0, input.length()), focused.end.coerceIn(0, input.length()))
        syncing = false
    }
    private fun requestSubmit() { if (target != null && !closed) { submitPending = true; sendPending() } }
    private fun sendPending() {
        handler.removeCallbacks(flush)
        val focused = target ?: return
        if (closed || busy) return
        val ticket = epoch
        if (dirty) {
            val text = input.text.toString()
            val cursor = input.selectionStart.coerceIn(0, text.length)
            dirty = false; busy = true; status.setText(R.string.keyboard_live_sending); refreshButtons()
            controller.replaceText(session, focused, text, cursor) { success, message ->
                if (closed || ticket != epoch) return@replaceText
                busy = false
                if (success) {
                    status.setText(R.string.keyboard_live_ready)
                    if (dirty || submitPending) handler.post(flush)
                } else { submitPending = false; status.text = message }
                refreshButtons()
            }
        } else if (submitPending) {
            submitPending = false; busy = true; refreshButtons()
            controller.submitText(session, focused) { success, message ->
                if (closed || ticket != epoch) return@submitText
                busy = false
                status.text = if (success) activity.getString(R.string.keyboard_live_submitted) else message
                if (dirty) handler.post(flush)
                refreshButtons()
            }
        }
    }
    private fun refreshButtons() { dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled = target != null && !closed }
    companion object { const val EDIT_DELAY_MS = 120L }
}
