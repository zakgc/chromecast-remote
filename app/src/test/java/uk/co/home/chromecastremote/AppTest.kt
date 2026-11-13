package uk.co.home.chromecastremote

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import uk.co.home.chromecastremote.protocol.Identity

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 35])
@GraphicsMode(GraphicsMode.Mode.LEGACY)
class AppTest {
    private lateinit var store: DeviceStore
    private var activity: ActivityController<MainActivity>? = null
    @Before fun prepare() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("home_remote", Context.MODE_PRIVATE).edit().clear().commit()
        store = DeviceStore(context)
    }
    @After fun finish() { activity?.pause()?.stop()?.destroy() }
    private fun launch(): MainActivity {
        activity = Robolectric.buildActivity(MainActivity::class.java).setup()
        return activity!!.get()
    }
    private fun views(root: View): List<View> = listOf(root) + if (root is ViewGroup) (0 until root.childCount).flatMap { views(root.getChildAt(it)) } else emptyList()
    private fun buttons(activity: MainActivity) = views(activity.window.decorView).filterIsInstance<Button>()
    private fun click(activity: MainActivity, text: String) = buttons(activity).first { it.text.toString() == text }.performClick()
    @Test fun firstLaunchShowsRemoteWithDisabledCommandsAndUsableSetup() {
        val app = launch()
        val up = buttons(app).first { it.contentDescription == "Up" }
        assertFalse(up.isEnabled)
        click(app, "+ Add a TV")
        assertTrue(buttons(app).any { it.text == "Add by IP address" })
        click(app, "← Remote")
        assertTrue(buttons(app).any { it.contentDescription == "Select" })
    }
    @Test fun savedSelectionAndOneTapSwitchingKeepControlsDisabledUntilConnected() {
        val living = TvDevice(name = "Living Room", host = "192.168.1.20")
        val bedroom = TvDevice(name = "Bedroom", host = "192.168.1.21")
        store.save(living); store.save(bedroom); store.selected = bedroom.id
        val app = launch()
        assertTrue(buttons(app).first { it.text.startsWith("Bedroom\n") }.isSelected)
        buttons(app).first { it.text.startsWith("Living Room\n") }.performClick()
        assertEquals(living.id, store.selected)
        assertTrue(buttons(app).first { it.text.startsWith("Living Room\n") }.isSelected)
        assertFalse(buttons(app).first { it.contentDescription == "Up" }.isEnabled)
    }
    @Test fun optionalControlVisibilityFollowsEachDeviceAfterSwitch() {
        val living = TvDevice(name = "Living Room", host = "192.168.1.20", volumeVerified = true)
        val bedroom = TvDevice(name = "Bedroom", host = "192.168.1.21", playbackVerified = true)
        store.save(living); store.save(bedroom); store.selected = living.id
        val app = launch()
        assertTrue(buttons(app).any { it.text == "Vol +" })
        assertFalse(buttons(app).any { it.text == "Play / pause" })
        buttons(app).first { it.text.startsWith("Bedroom\n") }.performClick()
        assertFalse(buttons(app).any { it.text == "Vol +" })
        assertTrue(buttons(app).any { it.text == "Play / pause" })
    }
    @Test fun savedPairingAndSeparateTestRecordsSurviveStoreReloadAndRemoval() {
        val living = TvDevice(name = "Living Room", host = "192.168.1.20", fingerprint = "test-pin-a")
        val bedroom = TvDevice(name = "Bedroom", host = "192.168.1.21", fingerprint = "test-pin-b")
        store.save(living); store.save(bedroom); store.selected = bedroom.id
        store.testResult(living.id, "Home", "Passed")
        store.testResult(bedroom.id, "Home", "Failed")
        val reloaded = DeviceStore(RuntimeEnvironment.getApplication())
        assertEquals(listOf(living, bedroom), reloaded.devices())
        reloaded.remove(bedroom.id)
        assertEquals(living.id, reloaded.selected)
        assertEquals("Passed", reloaded.testResults(living.id).getString("Home"))
        assertEquals(0, reloaded.testResults(bedroom.id).length())
        assertEquals("test-pin-a", reloaded.devices().single().fingerprint)
    }
    @Test fun reportsStayUntestedUntilExplicitlyRecorded() {
        val device = TvDevice(name = "Living Room", host = "192.168.1.20")
        store.save(device)
        val app = launch()
        click(app, "Test this setup")
        assertTrue(buttons(app).any { it.text == "Home\nNot tested" })
        assertFalse(buttons(app).first { it.text == "Play / pause" }.isEnabled)
        assertEquals(0, store.testResults(device.id).length())
        assertTrue(views(app.window.decorView).filterIsInstance<TextView>().any { it.text.contains("Android") })
    }
    @Test fun upgradingAnOldPairingPreservesItsNameAndTheOtherTvsIdentity() {
        val context = RuntimeEnvironment.getApplication()
        val oldData = JSONArray().put(JSONObject().put("id", "old-living").put("name", "Living Room")
            .put("host", "192.168.1.20").put("pin", "old-pin"))
            .put(JSONObject().put("id", "old-bedroom").put("name", "Bedroom")
                .put("host", "192.168.1.21").put("pin", "other-pin"))
        context.getSharedPreferences("home_remote", Context.MODE_PRIVATE).edit().putString("devices", oldData.toString()).commit()
        val legacy = store.devices().first()
        assertEquals(1, legacy.identityVersion)
        assertEquals(1, store.devices().last().identityVersion)
        // A new pairing key does not change which key is used by a saved connection before success.
        assertNotEquals(Identity.aliasFor(legacy.id, legacy.identityVersion), Identity.aliasFor(legacy.id, Identity.CURRENT_VERSION))
        assertEquals("old-pin", DeviceStore(context).devices().first().fingerprint)
        store.save(legacy.copy(fingerprint = "new-pin", identityVersion = Identity.CURRENT_VERSION))
        val saved = DeviceStore(context).devices()
        assertEquals("Living Room", saved.first().name)
        assertEquals("192.168.1.20", saved.first().host)
        assertEquals(Identity.CURRENT_VERSION, saved.first().identityVersion)
        assertEquals("new-pin", saved.first().fingerprint)
        assertEquals("other-pin", saved.last().fingerprint)
        assertEquals(1, saved.last().identityVersion)
    }
}
