package uk.co.home.chromecastremote

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.text.InputFilter
import android.text.InputType
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.WindowInsets
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import uk.co.home.chromecastremote.protocol.Identity
import uk.co.home.chromecastremote.protocol.PairingSession
import uk.co.home.chromecastremote.protocol.RemoteProtocol
import java.io.File
import java.util.concurrent.Executors
import java.util.UUID

class MainActivity : Activity() {
    private val ink = Color.rgb(237, 244, 239)
    private val muted = Color.rgb(173, 193, 186)
    private val surface = Color.rgb(32, 48, 45)
    private val accent = Color.rgb(183, 231, 210)
    private lateinit var store: DeviceStore
    private lateinit var controller: RemoteController
    private lateinit var discovery: Discovery
    private lateinit var connectivity: ConnectivityManager
    private lateinit var body: LinearLayout
    private lateinit var scroll: ScrollView
    private var screen = "remote"
    private var statusView: TextView? = null
    private var detailView: TextView? = null
    private val tabs = mutableListOf<Pair<String, Button>>()
    private val controls = mutableListOf<Pair<Button, Int>>()
    private val appButtons = mutableListOf<Button>()
    private lateinit var repeatPress: HoldToRepeat
    private var keyboardDialog: TvKeyboardDialog? = null
    private var suppressedKeyboardScope: String? = null
    private var editingFavourites = false
    private val found = linkedMapOf<String, FoundTv>()
    private var discoveryMessage = "Search for a Chromecast on your home network."
    private var discoveryStatus: TextView? = null
    private var foundList: LinearLayout? = null
    private val pairingIo = Executors.newSingleThreadExecutor()
    private var pairing: PairingSession? = null
    private var pairingDialog: AlertDialog? = null
    private var pairingToken = 0
    private var visible = false
    private var registered = false
    private var firstLayoutMs = 0L
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = updateNetwork()
        override fun onLost(network: Network) = updateNetwork()
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) = updateNetwork()
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val start = SystemClock.elapsedRealtime()
        store = DeviceStore(this)
        connectivity = getSystemService(ConnectivityManager::class.java)
        controller = RemoteController { state -> updateState(state) }
        repeatPress = HoldToRepeat { controller.state.takeIf { it.connected }?.sessionId }
        discovery = Discovery(this, ::deviceFound) { message -> discoveryMessage = message; discoveryStatus?.text = message }
        scroll = ScrollView(this).apply { isFillViewport = true; setBackgroundColor(Color.rgb(17, 28, 27)); clipToPadding = false }
        body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(22), dp(16), dp(22), dp(24)) }
        scroll.addView(body)
        scroll.setOnApplyWindowInsetsListener { view, insets ->
            if (Build.VERSION.SDK_INT >= 30) {
                val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            } else {
                @Suppress("DEPRECATION")
                view.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop, insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
            }
            insets
        }
        setContentView(scroll)
        showRemote()
        scroll.post { firstLayoutMs = SystemClock.elapsedRealtime() - start }
    }
    override fun onStart() {
        super.onStart(); visible = true
        val request = NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET).build()
        connectivity.registerNetworkCallback(request, networkCallback); registered = true
        controller.keyboard(store.automaticKeyboard)
        controller.resume(selected(), hasLan())
        discovery.start()
    }
    override fun onStop() {
        visible = false
        if (registered) { connectivity.unregisterNetworkCallback(networkCallback); registered = false }
        repeatPress.cancel(); discovery.close(); cancelPairing(); controller.pause()
        keyboardDialog?.close(); keyboardDialog = null
        super.onStop()
    }
    override fun onDestroy() { controller.close(); pairingIo.shutdownNow(); super.onDestroy() }
    private fun updateNetwork() { runOnUiThread { if (visible) controller.networkChanged(hasLan()) } }
    @Suppress("DEPRECATION") // Snapshot complements callbacks and detects local-only Wi-Fi even when mobile data is the default.
    private fun hasLan() = connectivity.allNetworks.any { network -> connectivity.getNetworkCapabilities(network)?.let {
        it.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || it.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    } == true }
    private fun selected() = store.devices().firstOrNull { it.id == store.selected } ?: store.devices().firstOrNull()
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun background(color: Int, radius: Int = 18) = GradientDrawable().apply { setColor(color); cornerRadius = dp(radius).toFloat() }
    private fun buttonBackground(color: Int) = RippleDrawable(ColorStateList.valueOf(Color.argb(70, 120, 160, 145)), background(color), background(Color.WHITE))
    private fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    private fun row() = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    private fun label(value: String, size: Float = 16f, color: Int = ink) = TextView(this).apply {
        text = value; textSize = size; setTextColor(color); setPadding(0, dp(8), 0, dp(8))
    }
    private fun button(value: String, primary: Boolean = false, action: () -> Unit): Button = Button(this).apply {
        text = value; textSize = 16f; isAllCaps = false
        setTextColor(if (primary) Color.rgb(17, 28, 27) else ink)
        background = buttonBackground(if (primary) accent else surface)
        minHeight = dp(60); minimumHeight = dp(60)
        setPadding(dp(12), dp(12), dp(12), dp(12))
        setOnClickListener { action() }
    }
    private fun LinearLayout.add(view: View, height: Int = -2) {
        addView(view, LinearLayout.LayoutParams(-1, if (height > 0) dp(height) else height).apply { bottomMargin = dp(10) })
    }
    private fun LinearLayout.weighted(view: View) { addView(view, LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(4), dp(4), dp(4), dp(4)) }) }
    private fun reset(title: String, subtitle: String) {
        repeatPress.cancel()
        body.removeAllViews(); tabs.clear(); controls.clear(); appButtons.clear(); statusView = null; detailView = null; discoveryStatus = null; foundList = null
        body.add(label("HOME REMOTE  /  ${BuildConfig.VERSION_NAME}", 12f, accent))
        body.add(label(title, 32f).apply { typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL) })
        body.add(label(subtitle, 15f, muted))
        scroll.smoothScrollTo(0, 0)
    }
    private fun showRemote() {
        screen = "remote"
        reset("A little closer.", "Your Google TV, within reach.")
        val devices = store.devices()
        val deviceRow = row()
        devices.forEach { device ->
            val tab = button(device.name) { store.selected = device.id; controller.select(device); showRemote() }
            tabs.add(device.id to tab); deviceRow.weighted(tab)
        }
        if (devices.size < 2) deviceRow.weighted(button(if (devices.isEmpty()) "+ Add a TV" else "+ Second TV") { showDevices() })
        body.add(deviceRow)
        statusView = label(controller.state.label, 18f, accent).also { it.accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE; body.add(it) }
        detailView = label("", 14f, muted).also { body.add(it) }
        if (devices.isEmpty()) body.add(label("First, turn on your Chromecast. Connect this phone to the same Wi-Fi, then add your TV.", 17f))
        val pad = column().apply { setPadding(dp(4), dp(8), dp(4), dp(8)) }
        val keys = listOf(listOf(null, "↑" to 19, null), listOf("←" to 21, "OK" to 23, "→" to 22), listOf(null, "↓" to 20, null))
        val descriptions = mapOf(19 to "Up", 20 to "Down", 21 to "Left", 22 to "Right", 23 to "Select")
        keys.forEach { line ->
            val r = row()
            line.forEach { key ->
                if (key == null) r.weighted(View(this).apply { minimumHeight = dp(68) })
                else r.weighted(remoteButton(key.first, key.second, key.second == 23).apply {
                    contentDescription = descriptions[key.second]; textSize = if (key.second == 23) 20f else 28f
                    minHeight = dp(76)
                })
            }
            pad.add(r)
        }
        body.add(pad)
        body.add(row().apply { weighted(remoteButton("Back", 4)); weighted(remoteButton("Home", 3)) })
        val device = selected()
        if (device?.volumeVerified == true) body.add(row().apply {
            weighted(remoteButton("Vol −", 25).apply { contentDescription = "Volume down" })
            weighted(remoteButton("Mute", 164))
            weighted(remoteButton("Vol +", 24).apply { contentDescription = "Volume up" })
        })
        if (device?.playbackVerified == true) body.add(remoteButton("Play / pause", 85))
        body.add(button("Keyboard") { showKeyboard() }.apply { isEnabled = device?.paired == true })
        if (device != null) {
            body.add(label("Favourite apps", 21f))
            store.favourites(device.id).chunked(2).forEach { pair ->
                val favouriteRow = row()
                pair.forEach { app ->
                    val launch = button(app.name) {
                        repeatPress.cancel()
                        if (controller.launchApp(app)) toast("Opening ${app.name} on ${device.name}…")
                        else toast("Connect to a TV that supports app shortcuts first.")
                    }
                    appButtons.add(launch); favouriteRow.weighted(launch)
                }
                body.add(favouriteRow)
            }
            body.add(button("Manage favourite apps") { chooseFavourites(device) })
        }
        body.add(button("Reconnect") { controller.select(selected()) })
        body.add(button("Devices & setup") { showDevices() })
        body.add(button("Test this setup") { showTests() })
        body.add(label("Local Wi-Fi · No account · No cloud", 13f, muted))
        updateState(controller.state)
    }
    private fun remoteButton(title: String, key: Int, primary: Boolean = false): Button {
        val view = button(title, primary) {
            if (controller.state.connected) {
                if (store.haptics) body.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                controller.send(key)
            }
        }
        controls.add(view to key)
        if (key in 19..22) repeatPress.attach(view)
        return view
    }
    private fun updateState(state: RemoteState) {
        if (!state.connected) repeatPress.cancel()
        keyboardDialog?.update(state)
        if (state.textTarget == null) suppressedKeyboardScope = null
        val scope = keyboardScope(state)
        if (visible && screen == "remote" && !editingFavourites && store.automaticKeyboard && keyboardDialog == null && scope != null && scope != suppressedKeyboardScope) showKeyboard()
        statusView?.text = state.label
        detailView?.text = state.details
        controls.forEach { (button, _) -> button.isEnabled = state.connected; button.alpha = if (state.connected) 1f else 0.35f }
        appButtons.forEach { button ->
            button.isEnabled = state.connected && state.features and RemoteProtocol.APP_LINKS != 0
            button.alpha = if (button.isEnabled) 1f else 0.35f
        }
        tabs.forEach { (id, button) ->
            val active = id == selected()?.id
            val device = store.devices().firstOrNull { it.id == id }
            button.text = getString(R.string.device_tab, device?.name.orEmpty(), if (active) state.label else if (device?.paired == true) "Tap to switch" else "Needs pairing")
            button.setTextColor(if (active) Color.rgb(17, 28, 27) else ink)
            button.background = buttonBackground(if (active) accent else surface)
            button.isSelected = active
            button.contentDescription = "${device?.name}, ${if (active) "selected, ${state.label}" else "tap to switch"}"
        }
    }
    private fun keyboardScope(state: RemoteState): String? = state.textTarget?.takeIf { state.connected }?.let {
        "${state.deviceId}:${state.sessionId}:${it.fieldId}:${it.focusVersion}"
    }
    private fun showKeyboard() {
        val device = selected() ?: return
        if (keyboardDialog != null) return
        repeatPress.cancel()
        lateinit var panel: TvKeyboardDialog
        panel = TvKeyboardDialog(this, device, controller, { store.automaticKeyboard }) {
            if (keyboardDialog === panel) {
                suppressedKeyboardScope = panel.lastBinding
                keyboardDialog = null
                val next = keyboardScope(controller.state)
                if (visible && screen == "remote" && !editingFavourites && store.automaticKeyboard && next != null && next != suppressedKeyboardScope) showKeyboard()
            }
        }
        keyboardDialog = panel
        panel.show()
    }
    private fun chooseFavourites(device: TvDevice) {
        editingFavourites = true
        val apps = store.favourites(device.id)
        val dialog = AlertDialog.Builder(this).setTitle("Favourite apps · ${device.name}")
            .setItems((apps.map { it.name } + "+ Add an app").toTypedArray()) { _, index ->
                if (index == apps.size) editFavourite(device, null) else favouriteActions(device, apps[index])
            }.setNegativeButton("Done") { _, _ -> editingFavourites = false; showRemote() }.create()
        dialog.setOnCancelListener { editingFavourites = false }
        dialog.show()
    }
    private fun favouriteActions(device: TvDevice, app: FavouriteApp) {
        val dialog = AlertDialog.Builder(this).setTitle(app.name)
            .setItems(arrayOf("Edit name or launch link", "Move earlier", "Move later", "Remove button")) { _, action ->
                if (action == 0) { editFavourite(device, app); return@setItems }
                val apps = store.favourites(device.id).toMutableList()
                val index = apps.indexOfFirst { it.id == app.id }
                if (index >= 0) when (action) {
                    1 -> if (index > 0) { apps.removeAt(index); apps.add(index - 1, app) }
                    2 -> if (index < apps.lastIndex) { apps.removeAt(index); apps.add(index + 1, app) }
                    3 -> apps.removeAt(index)
                }
                store.favourites(device.id, apps)
                showRemote(); chooseFavourites(device)
            }.setNegativeButton("Back") { _, _ -> chooseFavourites(device) }.create()
        dialog.setOnCancelListener { editingFavourites = false }
        dialog.show()
    }
    private fun editFavourite(device: TvDevice, app: FavouriteApp?) {
        if (app == null && store.favourites(device.id).size >= 12) { toast("You can save up to 12 buttons per TV."); chooseFavourites(device); return }
        val form = column().apply { setPadding(dp(20), dp(8), dp(20), 0) }
        val name = EditText(this).apply { hint = "Button name"; setSingleLine(); setText(app?.name.orEmpty()); filters = arrayOf(InputFilter.LengthFilter(40)) }
        val link = EditText(this).apply { hint = "App launch link"; setSingleLine(); inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI; setText(app?.link.orEmpty()); filters = arrayOf(InputFilter.LengthFilter(2048)) }
        form.add(name); form.add(link)
        val dialog = AlertDialog.Builder(this).setTitle(if (app == null) "Add favourite app" else "Edit favourite app")
            .setMessage("Use a launch link supported by the TV app. Installed-app browsing is unavailable through this connection.")
            .setView(form).setNegativeButton("Cancel") { _, _ -> chooseFavourites(device) }.setPositiveButton("Save", null).create()
        dialog.setOnCancelListener { editingFavourites = false }
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val title = name.text.toString().trim()
                val url = link.text.toString().trim()
                if (title.isEmpty()) { name.error = "Enter a button name"; return@setOnClickListener }
                if (!FavouriteApps.validLink(url)) { link.error = "Enter a supported app launch link, for example nuvio://home"; return@setOnClickListener }
                val updated = FavouriteApp(app?.id ?: UUID.randomUUID().toString(), title, url)
                val apps = store.favourites(device.id).toMutableList()
                val index = apps.indexOfFirst { it.id == updated.id }
                if (index >= 0) apps[index] = updated else apps.add(updated)
                store.favourites(device.id, apps); dialog.dismiss()
                showRemote(); chooseFavourites(device)
            }
        }
        dialog.show()
    }
    private fun showDevices() {
        screen = "devices"
        reset("Your televisions.", "Pair each phone separately with each Chromecast.")
        body.add(button("← Remote") { showRemote() })
        store.devices().forEach { device ->
            body.add(label(device.name, 24f))
            body.add(label("${device.host}:${device.port} · ${if (device.paired) "Pairing saved" else "Not paired"}", 14f, muted))
            body.add(button(if (device.paired) "Pair again" else "Pair with this TV") { beginPairing(device) })
            body.add(row().apply {
                weighted(button("Rename / IP") { editDevice(device) })
                weighted(button("Reconnect") { store.selected = device.id; controller.select(device); showRemote() })
            })
            body.add(button("Remove ${device.name}") {
                AlertDialog.Builder(this).setTitle("Remove ${device.name}?")
                    .setMessage("This removes its saved pairing from this phone. You can add and pair it again later.")
                    .setNegativeButton("Keep", null).setPositiveButton("Remove") { _, _ ->
                        controller.select(null); Identity.forget(device.id); store.remove(device.id); controller.select(selected()); showDevices()
                    }.show()
            })
        }
        body.add(label("Find a Chromecast", 24f))
        discoveryStatus = label(discoveryMessage, 14f, muted).also { it.accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE; body.add(it) }
        body.add(button("Search again") { found.clear(); renderFound(); discovery.start() })
        foundList = column().also { body.add(it) }
        renderFound()
        if (store.devices().size < 2) body.add(button("Add by IP address") { editDevice(null) })
        body.add(CheckBox(this).apply { setText(R.string.haptic_setting); textSize = 16f; setTextColor(ink); minHeight = dp(56); isChecked = store.haptics; setOnCheckedChangeListener { _, enabled -> store.haptics = enabled } })
        body.add(CheckBox(this).apply {
            setText(R.string.automatic_keyboard); textSize = 16f; setTextColor(ink); minHeight = dp(56); isChecked = store.automaticKeyboard
            setOnCheckedChangeListener { _, enabled -> store.automaticKeyboard = enabled; suppressedKeyboardScope = null; controller.keyboard(enabled) }
        })
        body.add(button("Connection help") { help() })
        body.add(label("This test build uses the unofficial Android TV remote protocol. A Google TV update may affect compatibility. Pairing keys stay protected on this phone. No analytics or sign-in.", 14f, muted))
    }
    private fun deviceFound(tv: FoundTv) {
        found[tv.name] = tv
        store.devices().firstOrNull { it.serviceName == tv.name }?.let { saved ->
            if (saved.host != tv.host || saved.port != tv.port) {
                val updated = saved.copy(host = tv.host, port = tv.port)
                store.save(updated)
                if (selected()?.id == saved.id) controller.select(updated)
            }
        }
        renderFound()
    }
    private fun renderFound() {
        val list = foundList ?: return
        list.removeAllViews()
        found.values.forEach { tv ->
            val saved = store.devices().any { it.serviceName == tv.name || it.host == tv.host }
            list.add(button("${tv.name}\n${if (saved) "Already added" else tv.host}") {
                if (saved) toast("This TV is already saved above.")
                else if (store.devices().size >= 2) toast("Remove a saved TV before adding its replacement.")
                else editDevice(null, tv)
            })
        }
    }
    private fun editDevice(device: TvDevice?, tv: FoundTv? = null) {
        val form = column().apply { setPadding(dp(20), dp(8), dp(20), 0) }
        val name = EditText(this).apply { hint = "Name, e.g. Living Room"; setSingleLine(); setText(device?.name ?: tv?.name.orEmpty()); filters = arrayOf(InputFilter.LengthFilter(40)) }
        val host = EditText(this).apply { hint = "TV IP address"; setSingleLine(); inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI; setText(device?.host ?: tv?.host.orEmpty()) }
        form.add(name); form.add(host)
        val dialog = AlertDialog.Builder(this).setTitle(if (device == null) "Add a TV" else "Edit TV")
            .setView(form).setNegativeButton("Cancel", null).setPositiveButton("Save", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val address = host.text.toString().trim()
                val title = name.text.toString().trim()
                if (title.isEmpty()) { name.error = "Give this TV a name"; return@setOnClickListener }
                if (!validAddress(address)) { host.error = "Enter an IPv4 or IPv6 address from the TV's network settings"; return@setOnClickListener }
                val updated = device?.copy(name = title, host = address) ?: TvDevice(name = title, host = address, serviceName = tv?.name.orEmpty(), port = tv?.port ?: 6466)
                store.save(updated); dialog.dismiss(); showDevices()
                if (selected()?.id == updated.id && updated.paired) controller.select(updated)
                if (!updated.paired) beginPairing(updated)
            }
        }
        dialog.show()
    }
    private fun validAddress(value: String): Boolean {
        if (value.contains(':')) return value.matches(Regex("[0-9a-fA-F:]+(%[a-zA-Z0-9_.-]+)?")) && runCatching { java.net.InetAddress.getByName(value) is java.net.Inet6Address }.getOrDefault(false)
        val parts = value.split('.')
        return parts.size == 4 && parts.all { it.isNotEmpty() && it.all(Char::isDigit) && (it.toIntOrNull() ?: -1) in 0..255 }
    }
    private fun beginPairing(device: TvDevice) {
        cancelPairing()
        controller.select(null)
        val token = pairingToken
        val session = PairingSession(device)
        pairing = session
        val waiting = AlertDialog.Builder(this).setTitle("Pair ${device.name}")
            .setMessage("Asking your Chromecast for a code…\n\nKeep the TV awake and on the same Wi-Fi.")
            .setNegativeButton("Cancel") { _, _ -> cancelPairing(); controller.select(selected()) }.create()
        waiting.setOnCancelListener { cancelPairing(); controller.select(selected()) }
        pairingDialog = waiting; waiting.show()
        pairingIo.execute {
            try {
                session.begin()
                runOnUiThread { if (visible && token == pairingToken) { waiting.dismiss(); codeDialog(device, session, token) } }
            } catch (error: Exception) { runOnUiThread { if (visible && token == pairingToken) pairingError(error) } }
        }
    }
    private fun codeDialog(device: TvDevice, session: PairingSession, token: Int) {
        val input = EditText(this).apply { hint = "ABC123"; setSingleLine(); inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS; filters = arrayOf(InputFilter.LengthFilter(6), InputFilter.AllCaps()) }
        val form = column().apply { setPadding(dp(24), dp(12), dp(24), 0); add(input) }
        val dialog = AlertDialog.Builder(this).setTitle("Enter the TV code")
            .setMessage("Enter the six characters shown on ${device.name}. This confirms the TV's identity.")
            .setView(form).setPositiveButton("Pair", null).setNegativeButton("Cancel") { _, _ -> cancelPairing(); controller.select(selected()) }.create()
        pairingDialog = dialog
        dialog.setOnCancelListener { cancelPairing(); controller.select(selected()) }
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val code = input.text.toString()
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
                pairingIo.execute {
                    try {
                        val pin = session.finish(code)
                        runOnUiThread {
                            if (!visible || token != pairingToken) return@runOnUiThread
                            store.save(device.copy(fingerprint = pin, identityVersion = Identity.CURRENT_VERSION)); store.selected = device.id
                            cancelPairing(); controller.select(selected()); showRemote(); toast("Pairing saved on this phone")
                        }
                    } catch (error: IllegalArgumentException) {
                        runOnUiThread { if (visible && token == pairingToken) { input.error = error.message; dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = true } }
                    } catch (error: Exception) { runOnUiThread { if (visible && token == pairingToken) pairingError(error) } }
                }
            }
        }
        dialog.show()
    }
    private fun pairingError(error: Exception) {
        cancelPairing(); controller.select(selected())
        AlertDialog.Builder(this).setTitle("Couldn't pair this TV").setMessage("${error.message.orEmpty()}\n\nCheck the TV is awake, the IP address is current, and both devices use the same Wi-Fi. Guest networks may block connections.")
            .setPositiveButton("OK", null).setNeutralButton("Help") { _, _ -> help() }.show()
    }
    private fun cancelPairing() { pairingToken++; pairing?.close(); pairing = null; pairingDialog?.dismiss(); pairingDialog = null }
    private fun showTests() {
        val device = selected()
        if (device == null) { showDevices(); return }
        screen = "tests"
        reset("Try it on your TV.", "${device.name} · ${Build.MODEL} · Android ${Build.VERSION.RELEASE}")
        body.add(button("← Remote") { showRemote() })
        statusView = label(controller.state.label, 18f, accent).also { body.add(it) }
        detailView = label(controller.state.details, 14f, muted).also { body.add(it) }
        body.add(label("Use the remote to test navigation first. Record what you actually see on the TV. Repeat on the other phone and Chromecast.", 16f))
        body.add(label("Optional controls — test only", 21f))
        body.add(label("Volume may depend on HDMI-CEC or the TV/soundbar setup. Infrared commands from the physical remote cannot be sent by this phone.", 14f, muted))
        body.add(row().apply { weighted(remoteButton("Vol −", 25)); weighted(remoteButton("Mute", 164)); weighted(remoteButton("Vol +", 24)) })
        body.add(remoteButton("Play / pause", 85))
        body.add(CheckBox(this).apply {
            setText(R.string.volume_verified); setTextColor(ink); textSize = 16f; minHeight = dp(56); isChecked = device.volumeVerified
            setOnCheckedChangeListener { _, value -> store.devices().firstOrNull { it.id == device.id }?.let { store.save(it.copy(volumeVerified = value)) } }
        })
        body.add(CheckBox(this).apply {
            setText(R.string.playback_verified); setTextColor(ink); textSize = 16f; minHeight = dp(56); isChecked = device.playbackVerified
            setOnCheckedChangeListener { _, value -> store.devices().firstOrNull { it.id == device.id }?.let { store.save(it.copy(playbackVerified = value)) } }
        })
        body.add(label("Compatibility record", 24f))
        val checks = listOf("Discovery", "Pairing", "Up / Down / Left / Right", "Select", "Back", "Home", "Switching: only selected TV responds", "Reopen: reconnect without pairing", "Sleep and wake", "TV restart", "Wi-Fi off and back on", "Volume and mute", "Play / pause", "Hold to repeat: stops on release / switch", "Keyboard: insert / delete / enter", "Favourite: Nuvio", "Favourite: SmartTube", "Large text and TalkBack")
        checks.forEach { check ->
            val result = store.testResults(device.id).optString(check, "Not tested")
            body.add(button("$check\n$result") {
                AlertDialog.Builder(this).setTitle(check).setItems(arrayOf("Passed", "Failed", "Not tested")) { _, index ->
                    store.testResult(device.id, check, listOf("Passed", "Failed", "Not tested")[index]); showTests()
                }.show()
            })
        }
        body.add(label("First layout: ~${firstLayoutMs} ms (activity estimate)\nAPK: ${"%.2f".format(File(applicationInfo.sourceDir).length() / 1048576.0)} MB\nConnection: shown above. TV response time, installed size and idle battery need device measurements.", 14f, muted))
        body.add(button("Share test report") { shareReport() })
        updateState(controller.state)
    }
    private fun shareReport() {
        val report = buildString {
            appendLine("Home Remote compatibility report — ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("App ${BuildConfig.VERSION_NAME} · First layout estimate ${firstLayoutMs} ms")
            appendLine("Connection: ${controller.state.label} · ${controller.state.details}")
            store.devices().forEach { device ->
                appendLine("\n${device.name} · ${device.host}:${device.port} · paired=${device.paired}")
                val results = store.testResults(device.id)
                results.keys().forEach { key -> appendLine("$key: ${results.getString(key)}") }
                appendLine("Volume verified=${device.volumeVerified}; playback verified=${device.playbackVerified}")
                appendLine("Favourite apps: ${store.favourites(device.id).joinToString { it.name }}")
            }
            appendLine("\nUnrecorded checks remain untested. No pairing secrets included.")
        }
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, report) }, "Share compatibility report"))
    }
    private fun help() {
        AlertDialog.Builder(this).setTitle("Getting connected")
            .setMessage("1. Wake the Chromecast with its physical remote.\n\n2. Put this phone and the Chromecast on the same home Wi-Fi. Avoid guest Wi-Fi and turn off a VPN if it blocks local devices.\n\n3. Search again. If discovery fails, find the TV's IP address in Google TV Settings → Network & Internet and add it manually.\n\n4. If the TV was reset or its identity changed, pair it again.\n\n5. Check Android TV Remote Service is installed and up to date. You do not need to turn on debugging.\n\nPairings are separate on each phone. Removing a TV deletes its credentials here; revoke old phone access in the TV settings if needed.\n\nThis prototype uses an unofficial local protocol. Test both TVs before relying on it.")
            .setPositiveButton("Got it", null).show()
    }
    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    @Deprecated("Uses the platform back callback for API 26 compatibility")
    @Suppress("DEPRECATION")
    override fun onBackPressed() { if (screen != "remote") showRemote() else super.onBackPressed() }
}
