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
import org.robolectric.shadows.ShadowAlertDialog
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
    private fun openSettings(app: MainActivity) = buttons(app).first { it.contentDescription == "Settings" }.performClick()
    private fun switchTv(app: MainActivity, index: Int) {
        buttons(app).first { it.contentDescription == "Choose TV" }.performClick()
        val dialog = ShadowAlertDialog.getLatestAlertDialog()
        dialog.listView.performItemClick(null, index, index.toLong())
    }
    @Test fun firstLaunchShowsRemoteWithDisabledCommandsAndUsableSetup() {
        val app = launch()
        val up = buttons(app).first { it.contentDescription == "Up" }
        assertFalse(up.isEnabled)
        assertFalse(buttons(app).any { it.text == "Apps" })
        click(app, "Add a TV")
        assertTrue(buttons(app).any { it.text == "Add by IP address" })
        click(app, "Back to settings")
        click(app, "Back to remote")
        assertTrue(buttons(app).any { it.contentDescription == "Select" })
    }
    @Test fun savedSelectionAndSelectorSwitchingKeepControlsDisabledUntilConnected() {
        val living = TvDevice(name = "Living Room", host = "192.168.1.20")
        val bedroom = TvDevice(name = "Bedroom", host = "192.168.1.21")
        store.save(living); store.save(bedroom); store.selected = bedroom.id
        val app = launch()
        assertTrue(views(app.window.decorView).filterIsInstance<TextView>().any { it.text == "Bedroom" })
        switchTv(app, 0)
        assertEquals(living.id, store.selected)
        assertTrue(views(app.window.decorView).filterIsInstance<TextView>().any { it.text == "Living Room" })
        assertFalse(buttons(app).first { it.contentDescription == "Up" }.isEnabled)
    }
    @Test fun playbackIsAvailableForBothTvsRegardlessOfOldVerificationFlags() {
        val living = TvDevice(name = "Living Room", host = "192.168.1.20", volumeVerified = true)
        val bedroom = TvDevice(name = "Bedroom", host = "192.168.1.21", playbackVerified = true)
        store.save(living); store.save(bedroom); store.selected = living.id
        val app = launch()
        assertFalse(buttons(app).any { it.text == "Vol +" })
        assertTrue(buttons(app).any { it.text == "Play / pause" })
        assertFalse(buttons(app).first { it.text == "Play / pause" }.isEnabled)
        switchTv(app, 1)
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
    @Test fun settingsKeepsConnectionHelpAndRemovesTheSetupTestingScreen() {
        val device = TvDevice(name = "Living Room", host = "192.168.1.20")
        store.save(device)
        val app = launch()
        openSettings(app)
        assertFalse(buttons(app).any { it.text == "Test this setup" })
        assertFalse(buttons(app).any { it.text == "Share test report" })
        click(app, "Connection help")
        assertTrue(ShadowAlertDialog.getLatestAlertDialog().isShowing)
        assertEquals(0, store.testResults(device.id).length())
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
    @Test fun favouriteSelectionsAreSeparatePerTvAndSurviveReopeningTheStore() {
        val living = TvDevice(name = "Living Room", host = "192.168.1.20")
        val bedroom = TvDevice(name = "Bedroom", host = "192.168.1.21")
        store.save(living); store.save(bedroom)
        assertTrue(store.favourites(living.id).isEmpty())
        store.favourites(living.id, listOf(FavouriteApps.all.first()))
        store.favourites(bedroom.id, emptyList())
        val reloaded = DeviceStore(RuntimeEnvironment.getApplication())
        assertEquals(listOf("nuvio"), reloaded.favourites(living.id).map { it.id })
        assertTrue(reloaded.favourites(bedroom.id).isEmpty())
        assertEquals(Identity.CURRENT_VERSION, reloaded.devices().first().identityVersion)
        reloaded.remove(living.id)
        assertTrue(reloaded.favourites(living.id).isEmpty())
    }
    @Test fun favouriteButtonsAreVisibleButCannotLaunchBeforeConnecting() {
        val device = TvDevice(name = "Living Room", host = "192.168.1.20")
        store.save(device); store.favourites(device.id, FavouriteApps.all)
        val app = launch()
        assertFalse(buttons(app).first { it.text == "Nuvio" }.isEnabled)
        assertFalse(buttons(app).first { it.text == "SmartTube" }.isEnabled)
        assertFalse(buttons(app).first { it.text == "Keyboard" }.isEnabled)
    }
    @Test fun customFavouriteButtonsCanBeRenamedReorderedAndRemovedWithoutABuiltInCatalogue() {
        val device = TvDevice(name = "Living Room", host = "192.168.1.20")
        store.save(device)
        val custom = FavouriteApp("custom", "My TV app", "my-tv-app://home")
        val other = FavouriteApp("another", "Another app", "another-app://start")
        store.favourites(device.id, listOf(custom, other))
        store.favourites(device.id, listOf(other, custom.copy(name = "Renamed", link = "my-tv-app://search")))
        val saved = DeviceStore(RuntimeEnvironment.getApplication()).favourites(device.id)
        assertEquals(listOf("Another app", "Renamed"), saved.map { it.name })
        assertEquals("my-tv-app://search", saved.last().link)
        store.favourites(device.id, saved.take(1))
        assertEquals(listOf(other), store.favourites(device.id))
    }
    @Test fun upgradeRetainsOldFavouriteChoicesAndCorrectsTheSmartTubeRootLink() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("home_remote", Context.MODE_PRIVATE)
        val device = TvDevice(name = "Living Room", host = "192.168.1.20")
        store.save(device)
        prefs.edit().remove("editable_favourites_v1").putString("favourites_${device.id}", "[\"smarttube\",\"nuvio\"]").commit()
        val upgraded = DeviceStore(context)
        assertEquals(listOf("smarttube", "nuvio"), upgraded.favourites(device.id).map { it.id })
        assertEquals("vnd.youtube.launch://youtube.com", upgraded.favourites(device.id).first().link)
        assertEquals(device, upgraded.devices().single())
        assertTrue(upgraded.automaticKeyboard)
    }
}
