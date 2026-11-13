package uk.co.home.chromecastremote

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.EditText
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Rect
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowAlertDialog
import uk.co.home.chromecastremote.protocol.RemoteProtocol
import java.io.File
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w390dp-h844dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class RemoteDesignTest {
    private lateinit var store: DeviceStore
    private var activity: ActivityController<MainActivity>? = null
    @Before fun prepare() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("home_remote", Context.MODE_PRIVATE).edit().clear().commit()
        store = DeviceStore(context)
        val guest = TvDevice(id = "guest", name = "Guest Room TV", host = "192.168.1.20", fingerprint = "saved-pin", playbackVerified = true)
        store.save(guest)
        store.save(TvDevice(id = "maria", name = "Maria TV", host = "192.168.1.21", fingerprint = "saved-pin"))
        store.selected = guest.id
        store.favourites(guest.id, FavouriteApps.all)
    }
    @After fun finish() { activity?.pause()?.stop()?.destroy() }
    private fun launch(): MainActivity {
        activity = Robolectric.buildActivity(MainActivity::class.java).setup()
        return activity!!.get()
    }
    private fun views(root: View): List<View> = listOf(root) + if (root is ViewGroup) (0 until root.childCount).flatMap { views(root.getChildAt(it)) } else emptyList()
    private fun root(app: MainActivity) = app.findViewById<ViewGroup>(android.R.id.content).getChildAt(0) as ScrollView
    private fun layout(app: MainActivity, width: Int, height: Int): ScrollView = root(app).apply {
        measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        layout(0, 0, width, height)
    }
    private fun button(app: MainActivity, text: String) = views(root(app)).filterIsInstance<Button>().first { it.text.toString() == text }
    private fun settings(app: MainActivity) = views(root(app)).first { it.contentDescription == "Settings" }.performClick()
    private fun connectedState(app: MainActivity) {
        // Render a deterministic connected state without opening a real network connection.
        MainActivity::class.java.getDeclaredMethod("updateState", RemoteState::class.java).apply { isAccessible = true }
            .invoke(app, RemoteState("guest", true, "Connected", sessionId = 1, features = RemoteProtocol.APP_LINKS))
    }
    private fun snapshot(root: View, name: String) {
        val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.translate(-root.scrollX.toFloat(), -root.scrollY.toFloat())
        root.draw(canvas)
        val file = File("build/outputs/design/$name.png").apply { parentFile!!.mkdirs() }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
    @Test fun compactAndRegularRemoteFitWithBottomControlsAndNoBranding() {
        val app = launch()
        connectedState(app)
        for ((width, height, name) in listOf(Triple(360, 684, "remote-compact"), Triple(390, 788, "remote-regular"))) {
            val scroll = layout(app, width, height)
            snapshot(scroll, name)
            val body = scroll.getChildAt(0) as RemoteBody
            assertTrue("$name must fit without scrolling: ${body.height} > $height", body.height <= height)
            val pad = views(body).filterIsInstance<CircularRemotePad>().single()
            assertTrue("D-pad centre stays in the lower half", pad.top + pad.height / 2 > height / 2)
            assertTrue("The enlarged pad should remain at least 280dp on these phones", pad.width >= 280)
            if (width == 390) assertEquals("Use the approved 340dp diameter on regular phones", 340, pad.width)
            val navigation = button(app, "Back").parent as View
            assertEquals(height - body.paddingBottom, navigation.bottom)
            assertTrue(button(app, "Back").height >= 48)
            val settings = views(body).first { it.contentDescription == "Settings" }
            val selector = views(body).first { it.contentDescription == "Choose TV" }.parent as View
            assertSame(selector.parent, settings.parent)
            assertEquals("Settings is square", settings.width, settings.height)
            assertEquals("Settings matches the device selector height", selector.height, settings.height)
            assertTrue("Settings must have a visible gap from the device selector", settings.left - selector.right >= 12)
            assertEquals(selector.top + selector.height / 2f, settings.top + settings.height / 2f, 1f)
            assertFalse(views(body).filterIsInstance<TextView>().any { it.text.contains(app.getString(R.string.app_name), true) })
        }
    }
    @Test fun settingsFitsNormalPhoneAndSwitchesSaveImmediately() {
        val app = launch(); settings(app)
        connectedState(app)
        val scroll = layout(app, 390, 744)
        snapshot(scroll, "settings")
        assertTrue("Settings should fit: ${scroll.getChildAt(0).height}", scroll.getChildAt(0).height <= 744)
        val toggles = views(scroll).filterIsInstance<Switch>()
        assertEquals(2, toggles.size)
        assertEquals(1, views(scroll).filterIsInstance<TextView>().count { it.text == "● Connected" })
        assertEquals(1, views(scroll).filterIsInstance<TextView>().count { it.text == "Pairing saved" })
        toggles.forEach { it.toggle() }
        val reloaded = DeviceStore(app)
        assertFalse(reloaded.haptics); assertFalse(reloaded.automaticKeyboard)
        snapshot(scroll, "settings-preferences-off")
        button(app, "Back to remote").performClick()
        settings(app)
        assertTrue(views(root(app)).filterIsInstance<Switch>().all { !it.isChecked })
        assertTrue(views(root(app)).filterIsInstance<TextView>().any { it.text == "Pairing saved" })
    }
    @Test fun extraFavouritesPageInPlaceAndCanBeEditedFromTheRemote() {
        val extras = (1..5).map { FavouriteApp("app$it", "App $it", "example://app$it") }
        store.favourites("guest", extras)
        val app = launch()
        assertTrue(button(app, "App 1").isShown)
        assertFalse(views(root(app)).filterIsInstance<Button>().any { it.text == "App 3" })
        button(app, "1/3 ›").performClick()
        assertTrue(button(app, "App 3").isShown)
        assertFalse(button(app, "App 3").isEnabled)
        button(app, "2/3 ›").performClick()
        assertTrue(button(app, "App 5").isShown)
        button(app, "3/3 ›").performClick()
        assertTrue(button(app, "App 1").isShown)
    }
    @Test fun largeTextScrollsToEveryControlAndSettingsPreference() {
        val app = launch()
        app.resources.configuration.fontScale = 1.8f
        @Suppress("DEPRECATION")
        app.resources.updateConfiguration(app.resources.configuration, app.resources.displayMetrics)
        // Rebuild using the larger text size.
        settings(app); button(app, "Back to remote").performClick()
        var scroll = layout(app, 320, 600)
        assertTrue(scroll.getChildAt(0).height > scroll.height)
        scroll.fullScroll(View.FOCUS_DOWN)
        assertTrue(button(app, "Back").isShown)
        settings(app)
        scroll = layout(app, 320, 600)
        assertTrue(scroll.getChildAt(0).height > scroll.height)
        scroll.fullScroll(View.FOCUS_DOWN)
        assertTrue(button(app, "Back to remote").isShown)
        snapshot(scroll, "settings-large-text")
    }
    @Test fun fullPadSegmentsRouteHoldsAndCancelWhenCrossingOrLifting() {
        val context = RuntimeEnvironment.getApplication()
        val presses = mutableListOf<Int>()
        val repeat = HoldToRepeat { 1L }
        val keys = (19..23).associateWith { key -> Button(context).apply { setOnClickListener { presses.add(key) }; if (key != 23) repeat.attach(this) } }
        val pad = CircularRemotePad(context, keys)
        pad.measure(View.MeasureSpec.makeMeasureSpec(280, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(280, View.MeasureSpec.EXACTLY))
        pad.layout(0, 0, 280, 280)
        fun touch(action: Int, x: Float, y: Float) {
            val event = MotionEvent.obtain(0, 0, action, x, y, 0)
            pad.dispatchTouchEvent(event); event.recycle()
        }
        // A point outside the Up button's rectangle, but inside the upper circular segment.
        touch(MotionEvent.ACTION_DOWN, 90f, 45f)
        assertEquals(listOf(19), presses)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(550))
        assertTrue(presses.size > 1)
        touch(MotionEvent.ACTION_MOVE, 250f, 140f)
        val count = presses.size
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
        assertEquals(count, presses.size)
        touch(MotionEvent.ACTION_DOWN, 250f, 140f); touch(MotionEvent.ACTION_UP, 250f, 140f)
        assertEquals(22, presses.last())
        val released = presses.size
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
        assertEquals(released, presses.size)
        repeat.cancel()
    }
    @Test fun directionGlyphsAreCentredWithinTheirButtonsAtPhoneDensity() {
        val app = launch(); connectedState(app)
        layout(app, 390, 788)
        val buttons = views(root(app)).filterIsInstance<RemoteIconButton>().filter { it.contentDescription in listOf("Up", "Down", "Left", "Right") }
        assertEquals(4, buttons.size)
        for (button in buttons) {
            val bitmap = Bitmap.createBitmap(button.width, button.height, Bitmap.Config.ARGB_8888)
            button.draw(Canvas(bitmap))
            val points = (0 until bitmap.height).flatMap { y -> (0 until bitmap.width).mapNotNull { x ->
                val color = bitmap.getPixel(x, y)
                if (Color.alpha(color) > 200 && Color.red(color) > 220) x to y else null
            } }
            assertTrue(points.isNotEmpty())
            assertEquals(button.width / 2f, (points.minOf { it.first } + points.maxOf { it.first }) / 2f, 1.5f)
            assertEquals(button.height / 2f, (points.minOf { it.second } + points.maxOf { it.second }) / 2f, 1.5f)
            bitmap.recycle()
        }
    }
    @Test fun settingsTvRowsAreReadOnlyAndManagementUsesTheStyledBackButton() {
        val app = launch(); settings(app)
        val name = views(root(app)).filterIsInstance<TextView>().first { it.text == "Maria TV" }
        val row = name.parent.parent as ViewGroup
        assertFalse(row.isClickable); assertFalse(row.isFocusable)
        assertEquals(1, row.childCount)
        button(app, "Manage TVs").performClick()
        val scroll = layout(app, 390, 788)
        assertFalse(views(scroll).filterIsInstance<TextView>().any { it.text.contains("Pair each phone separately") })
        val back = button(app, "Back to settings")
        assertNotNull(back.compoundDrawablesRelative[0])
        snapshot(scroll, "manage-tvs")
        back.performClick()
        assertTrue(button(app, "Back to remote").isShown)
    }
    @Test fun favouriteAndEditingDialogsUseTheAppPaletteAndKeepTheirActions() {
        val app = launch()
        button(app, "Edit").performClick()
        var dialog = ShadowAlertDialog.getLatestAlertDialog()
        fun renderDialog(name: String) {
            val decor = dialog.window!!.decorView
            decor.measure(View.MeasureSpec.makeMeasureSpec(350, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(700, View.MeasureSpec.AT_MOST))
            decor.layout(0, 0, decor.measuredWidth, decor.measuredHeight)
            snapshot(decor, name)
            assertFalse(dialog.getButton(AlertDialog.BUTTON_NEGATIVE).isAllCaps)
        }
        renderDialog("favourites-dialog")
        dialog.listView.performItemClick(null, 0, 0)
        dialog = ShadowAlertDialog.getLatestAlertDialog()
        renderDialog("favourite-actions-dialog")
        dialog.listView.performItemClick(null, 0, 0)
        dialog = ShadowAlertDialog.getLatestAlertDialog()
        renderDialog("edit-favourite-dialog")
        assertTrue(views(dialog.window!!.decorView).filterIsInstance<TextView>().any { it.text.toString() == "Nuvio" })
        dialog.dismiss()
    }
    @Test fun editTvDialogLeavesSpaceBetweenFieldsAndActionButtons() {
        val app = launch(); settings(app)
        button(app, "Manage TVs").performClick()
        button(app, "Rename / IP").performClick()
        val dialog = ShadowAlertDialog.getLatestAlertDialog()
        val decor = dialog.window!!.decorView
        decor.measure(View.MeasureSpec.makeMeasureSpec(350, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(700, View.MeasureSpec.AT_MOST))
        decor.layout(0, 0, decor.measuredWidth, decor.measuredHeight)
        snapshot(decor, "edit-tv-dialog")
        val fields = views(decor).filterIsInstance<EditText>()
        assertEquals(2, fields.size)
        assertTrue(fields[1].top - fields[0].bottom >= 10)
        val cancel = dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
        val save = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        assertTrue("Popup actions must be separated by at least 12dp", save.left - cancel.right >= 12)
        for (action in listOf(cancel, save)) {
            // Check the rendered hierarchy, independent of Robolectric's separate window coordinates.
            val bounds = Rect(0, 0, action.width, action.height)
            var child: View = action
            while (child !== decor) {
                val parent = child.parent as ViewGroup
                bounds.offset(child.left - parent.scrollX, child.top - parent.scrollY)
                if (parent.clipChildren) assertTrue("Action buttons must not be clipped", bounds.top >= 0 && bounds.bottom <= parent.height)
                child = parent
            }
        }
        dialog.dismiss()
    }
    @Test fun longDeviceNamesLoopOnOneLineWithoutResizingOrMovingTheControls() {
        val name = "Guest Room TV — Chromecast with Google TV"
        val device = store.devices().first { it.id == "guest" }
        store.save(device.copy(name = name))
        val app = launch()
        val scroll = layout(app, 360, 684)
        val label = views(scroll).filterIsInstance<LoopingDeviceName>().single()
        val settings = views(scroll).first { it.contentDescription == "Settings" }
        val selector = views(scroll).first { it.contentDescription == "Choose TV" }.parent as View
        assertEquals(1, label.lineCount)
        assertEquals(name, label.text.toString())
        assertTrue(label.isLooping)
        assertTrue("Long text overflows its single-line viewport", label.paint.measureText(name) > label.width)
        assertEquals(selector.height, settings.height)
        assertEquals(settings.height, settings.width)
        assertTrue((scroll.getChildAt(0) as View).height <= scroll.height)
        snapshot(scroll, "remote-long-name")
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))
        assertTrue("Overflow text moves after the initial reading pause", label.scrollOffset > 0)
        settings(app)
        assertFalse("Leaving the remote stops the animation", label.isLooping)
        button(app, "Back to remote").performClick()
        layout(app, 360, 684)
        val remote = root(app)
        views(remote).first { it.contentDescription == "Choose TV" }.performClick()
        val dialog = ShadowAlertDialog.getLatestAlertDialog()
        assertEquals("The full device name remains available in the selector", name, dialog.listView.adapter.getItem(0).toString())
        dialog.listView.performItemClick(null, 1, 1)
        layout(app, 360, 684)
        val short = views(root(app)).filterIsInstance<LoopingDeviceName>().single()
        assertTrue("Short names fit without scrolling", short.paint.measureText(short.text.toString()) <= short.width)
        assertFalse(short.isLooping)
    }
    @Test fun settingsGlyphIsGeometricallyCentredInItsSquare() {
        val app = launch(); layout(app, 390, 788)
        val settings = views(root(app)).first { it.contentDescription == "Settings" }
        assertEquals(settings.width, settings.height)
        val bitmap = Bitmap.createBitmap(settings.width, settings.height, Bitmap.Config.ARGB_8888)
        settings.draw(Canvas(bitmap))
        val points = (0 until bitmap.height).flatMap { y -> (0 until bitmap.width).mapNotNull { x ->
            val color = bitmap.getPixel(x, y)
            if (Color.alpha(color) > 200 && Color.red(color) > 220) x to y else null
        } }
        assertTrue(points.isNotEmpty())
        assertEquals(settings.width / 2f, (points.minOf { it.first } + points.maxOf { it.first }) / 2f, 1f)
        assertEquals(settings.height / 2f, (points.minOf { it.second } + points.maxOf { it.second }) / 2f, 1f)
        bitmap.recycle()
    }
}
