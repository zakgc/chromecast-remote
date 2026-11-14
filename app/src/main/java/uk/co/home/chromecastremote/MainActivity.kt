package uk.co.home.chromecastremote

import android.app.Activity
import android.app.AlertDialog
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
import android.text.InputFilter
import android.text.InputType
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import uk.co.home.chromecastremote.protocol.Identity
import uk.co.home.chromecastremote.protocol.PairingSession
import uk.co.home.chromecastremote.protocol.RemoteProtocol
import java.util.concurrent.Executors
import java.util.UUID

class MainActivity : Activity() {
    private val ink = RemoteColors.ink
    private val muted = RemoteColors.muted
    private val surface = RemoteColors.surface
    private val accent = RemoteColors.blue
    private lateinit var store: DeviceStore
    private lateinit var controller: RemoteController
    private lateinit var discovery: Discovery
    private lateinit var connectivity: ConnectivityManager
    private lateinit var body: RemoteBody
    private lateinit var scroll: ScrollView
    private var screen = "remote"
    private var statusView: TextView? = null
    private val tvStatuses = mutableListOf<Pair<String, TextView>>()
    private val controls = mutableListOf<Button>()
    private val appButtons = mutableListOf<Button>()
    private var favouritePage = 0
    private lateinit var repeatPress: HoldToRepeat
    private lateinit var selectPress: HoldToSelect
    private lateinit var vibration: ButtonVibration
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
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = updateNetwork()
        override fun onLost(network: Network) = updateNetwork()
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) = updateNetwork()
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = DeviceStore(this)
        vibration = ButtonVibration(this) { store.haptics }
        connectivity = getSystemService(ConnectivityManager::class.java)
        controller = RemoteController { state -> updateState(state) }
        repeatPress = HoldToRepeat { controller.state.takeIf { it.connected }?.sessionId }
        selectPress = HoldToSelect(
            { controller.state.takeIf { it.connected }?.sessionId },
            controller::holdSelect,
            { vibration.tap() }
        )
        discovery = Discovery(this, ::deviceFound) { message -> discoveryMessage = message; discoveryStatus?.text = message }
        scroll = RemoteViewport(this).apply { isFillViewport = true; setBackgroundColor(RemoteColors.background); clipToPadding = false }
        body = RemoteBody(this).apply { setPadding(dp(20), dp(12), dp(20), dp(16)) }
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
        cancelPresses(); discovery.close(); cancelPairing(); controller.pause()
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
    private fun background(color: Int, radius: Int = 14) = GradientDrawable().apply { setColor(color); cornerRadius = dp(radius).toFloat(); setStroke(dp(1), RemoteColors.edge) }
    private fun buttonBackground(color: Int) = RippleDrawable(ColorStateList.valueOf(Color.argb(65, 121, 169, 245)), background(color), background(Color.WHITE))
    private fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    private fun row() = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    private fun label(value: String, size: Float = 16f, color: Int = ink) = TextView(this).apply {
        text = value; textSize = size; setTextColor(color); setPadding(0, dp(8), 0, dp(8))
    }
    private fun button(value: String, primary: Boolean = false, direction: String? = null, iconSize: Int = 30, action: () -> Unit): Button = (if (direction == null) Button(this) else RemoteIconButton(this, direction, iconSize)).apply {
        text = value; textSize = 16f; isAllCaps = false
        setTextColor(if (primary) RemoteColors.background else ink)
        background = buttonBackground(if (primary) accent else surface)
        minHeight = dp(52); minimumHeight = dp(52)
        setPadding(dp(12), dp(12), dp(12), dp(12))
        setOnClickListener { vibration.tap(); action() }
    }
    private fun LinearLayout.add(view: View, height: Int = -2) {
        addView(view, LinearLayout.LayoutParams(-1, if (height > 0) dp(height) else height).apply { bottomMargin = dp(10) })
    }
    private fun LinearLayout.weighted(view: View) { addView(view, LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(4), dp(4), dp(4), dp(4)) }) }
    private fun clearScreen() {
        cancelPresses()
        body.pad = null
        body.removeAllViews(); tvStatuses.clear(); controls.clear(); appButtons.clear(); statusView = null; discoveryStatus = null; foundList = null
        scroll.scrollTo(0, 0)
    }
    private fun showRemote() {
        screen = "remote"
        clearScreen()
        val device = selected()
        val topBar = DeviceTopBar(this)
        val selector = row().apply { background = background(surface); setPadding(dp(12), dp(4), dp(4), dp(4)) }
        val selectorText = column()
        selectorText.addView(LoopingDeviceName(this).apply {
            text = device?.name ?: "Add a TV"; textSize = 20f; setTextColor(ink)
            typeface = Typeface.DEFAULT_BOLD; setPadding(0, dp(4), 0, 0)
        })
        val connection = row()
        statusView = label("", 14f, muted).apply {
            setPadding(0, 0, 0, 0); accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
            setSingleLine(); ellipsize = TextUtils.TruncateAt.END
        }.also { connection.addView(it, LinearLayout.LayoutParams(0, -2, 1f)) }
        selectorText.addView(connection)
        selectorText.isClickable = true; selectorText.isFocusable = true
        selectorText.contentDescription = "Choose TV, ${device?.name ?: "no TV saved"}"
        selectorText.setOnClickListener { chooseTv() }
        selector.addView(selectorText, LinearLayout.LayoutParams(0, -2, 1f))
        selector.addView(iconButton("Reconnect", "reconnect") { cancelPresses(); controller.select(selected()) })
        selector.addView(iconButton("Choose TV", "down") { chooseTv() })
        topBar.addView(selector, LinearLayout.LayoutParams(0, -2, 1f))
        topBar.addView(iconButton("Settings", "settings") { showSettings() }.apply {
            background = buttonBackground(surface)
        }, LinearLayout.LayoutParams(dp(56), dp(56)).apply { leftMargin = dp(12) })
        body.add(topBar)
        val pages = device?.let { store.favourites(it.id).chunked(2) }.orEmpty()
        favouritePage = favouritePage.coerceIn(0, (pages.size - 1).coerceAtLeast(0))
        val heading = row()
        heading.addView(label("Favourite apps", 18f).apply { typeface = Typeface.DEFAULT_BOLD }, LinearLayout.LayoutParams(0, -2, 1f))
        if (pages.size > 1) heading.addView(textAction("${favouritePage + 1}/${pages.size} ›") { favouritePage = (favouritePage + 1) % pages.size; showRemote() }.apply {
            contentDescription = "More favourites, page ${favouritePage + 1} of ${pages.size}"; textSize = 14f
        })
        heading.addView(textAction("Edit") { if (device != null) chooseFavourites(device) else showDevices() })
        body.addView(heading)
        if (device != null) {
            val favourites = row()
            pages.getOrNull(favouritePage)?.forEach { app ->
                val launch = button(app.name) {
                    cancelPresses()
                    if (controller.launchApp(app)) toast("Opening ${app.name} on ${device.name}…")
                    else toast("Connect to a TV that supports app shortcuts first.")
                }
                appButtons.add(launch)
                favourites.addView(launch, LinearLayout.LayoutParams(0, -2, 1f).apply { if (favourites.childCount > 0) leftMargin = dp(10) })
            }
            if (pages.isEmpty()) favourites.addView(button("Add favourite apps") { chooseFavourites(device) }, LinearLayout.LayoutParams(-1, -2))
            body.add(favourites)
        } else body.add(label("Add your TV to save app shortcuts.", 14f, muted))
        body.add(button("Keyboard") { showKeyboard() }.apply {
            setCompoundDrawablesRelative(icon("keyboard"), null, icon("right", 18), null)
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            compoundDrawablePadding = dp(16); isEnabled = device?.paired == true
        })
        if (device == null) body.add(button("Add a TV") { showDevices() })
        body.addView(View(this), LinearLayout.LayoutParams(-1, dp(20), 1f))
        val directions = mapOf(19 to ("Up" to "up"), 20 to ("Down" to "down"), 21 to ("Left" to "left"), 22 to ("Right" to "right"), 23 to ("Select" to ""))
        val keys = directions.mapValues { (key, value) ->
            remoteButton(if (key == 23) "OK" else "", key, key == 23, value.second.takeIf { key != 23 }).apply {
                contentDescription = value.first; setPadding(0, 0, 0, 0); minWidth = 0; minimumWidth = 0
                textSize = 26f; typeface = Typeface.DEFAULT_BOLD
                if (key == 23) background = RippleDrawable(ColorStateList.valueOf(Color.argb(60, 0, 0, 0)), GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(accent) }, null)
                else { background = clearRipple() }
            }
        }
        val pad = CircularRemotePad(this, keys)
        body.pad = pad
        body.addView(pad, LinearLayout.LayoutParams(dp(340), dp(340)).apply { gravity = Gravity.CENTER_HORIZONTAL; bottomMargin = dp(18) })
        val navigation = row()
        listOf(Triple("Back", 4, "back"), Triple("Home", 3, "home"), Triple("Play / pause", 85, "play"))
            .forEach { (title, key, glyph) ->
                navigation.addView(remoteButton("", key, direction = glyph, iconSize = 24).apply {
                    contentDescription = title
                    setPadding(dp(6), dp(12), dp(6), dp(12))
                    textSize = 14f; minWidth = 0; minimumWidth = 0; minHeight = dp(80)
                }, LinearLayout.LayoutParams(0, -2, 1f).apply { if (navigation.childCount > 0) leftMargin = dp(10) })
            }
        body.addView(navigation)
        updateState(controller.state)
    }
    private fun icon(kind: String, size: Int = 24) = RemoteIcon(kind).apply { setBounds(0, 0, dp(size), dp(size)) }
    private fun iconButton(description: String, glyph: String, action: () -> Unit) = button("", direction = glyph, iconSize = 24, action = action).apply {
        contentDescription = description; background = clearRipple()
        minWidth = dp(48); minimumWidth = dp(48); minHeight = dp(48); minimumHeight = dp(48)
        setPadding(dp(12), dp(12), dp(12), dp(12))
        layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
    }
    private fun textAction(title: String, action: () -> Unit) = button(title, action = action).apply {
        background = null; setTextColor(accent); minHeight = dp(48); minimumHeight = dp(48)
        setPadding(0, dp(8), dp(8), dp(8)); minWidth = dp(48); minimumWidth = dp(48); gravity = Gravity.CENTER_VERTICAL
    }
    private fun clearRipple() = RippleDrawable(ColorStateList.valueOf(Color.argb(65, 121, 169, 245)), null, background(Color.WHITE, 48))
    private fun chooseTv() {
        cancelPresses()
        val devices = store.devices()
        if (devices.isEmpty()) { showDevices(); return }
        AlertDialog.Builder(this).setTitle("Choose TV")
            .setSingleChoiceItems(devices.map { it.name }.toTypedArray(), devices.indexOfFirst { it.id == selected()?.id }) { dialog, index ->
                store.selected = devices[index].id; favouritePage = 0; controller.select(devices[index]); dialog.dismiss(); showRemote()
            }.setNeutralButton("Manage TVs") { _, _ -> showDevices() }.setNegativeButton("Cancel", null).show()
    }
    private fun remoteButton(title: String, key: Int, primary: Boolean = false, direction: String? = null, iconSize: Int = 30): Button {
        val view = button(title, primary, direction, iconSize) {
            if (controller.state.connected) {
                controller.send(key)
            }
        }
        controls.add(view)
        if (key in 19..22) repeatPress.attach(view)
        if (key == 23) selectPress.attach(view)
        return view
    }
    private fun cancelPresses() {
        repeatPress.cancel()
        selectPress.cancel()
    }
    private fun updateState(state: RemoteState) {
        if (!state.connected) cancelPresses()
        selectPress.cancelIfSessionChanged()
        keyboardDialog?.update(state)
        if (state.textTarget == null) suppressedKeyboardScope = null
        val scope = keyboardScope(state)
        if (visible && screen == "remote" && !editingFavourites && store.automaticKeyboard && keyboardDialog == null && scope != null && scope != suppressedKeyboardScope) showKeyboard()
        statusView?.text = if (screen == "remote") "● ${if (state.connected) "Connected" else if (selected()?.paired == false) "Pairing needed" else state.label}" else state.label
        if (screen == "remote") statusView?.setTextColor(if (state.connected) accent else muted)
        statusView?.contentDescription = listOf(state.label, state.details).filter { it.isNotBlank() }.joinToString(". ")
        controls.forEach { button -> button.isEnabled = state.connected; button.alpha = if (state.connected) 1f else 0.35f }
        appButtons.forEach { button ->
            button.isEnabled = state.connected && state.features and RemoteProtocol.APP_LINKS != 0
            button.alpha = if (button.isEnabled) 1f else 0.35f
        }
        tvStatuses.forEach { (id, status) ->
            val device = store.devices().firstOrNull { it.id == id }
            val connected = state.connected && state.deviceId == id
            status.text = if (connected) "● Connected" else if (device?.paired == true) "Pairing saved" else "Not paired"
            status.setTextColor(if (connected) accent else muted)
        }
    }
    private fun section(title: String) {
        body.addView(label(title, 18f).apply {
            typeface = Typeface.DEFAULT_BOLD; setPadding(0, dp(12), 0, dp(6))
            accessibilityHeadingCompat()
        })
    }
    private fun View.accessibilityHeadingCompat() { if (Build.VERSION.SDK_INT >= 28) isAccessibilityHeading = true }
    private fun group() = column().apply { background = background(surface); setPadding(dp(14), 0, dp(14), 0) }
    private fun LinearLayout.groupRow(view: View) {
        if (childCount > 0) addView(View(this@MainActivity).apply { setBackgroundColor(RemoteColors.edge) }, LinearLayout.LayoutParams(-1, dp(1)))
        addView(view, LinearLayout.LayoutParams(-1, -2))
    }
    private fun settingsLink(title: String, action: () -> Unit) = button(title, action = action).apply {
        background = clearRipple(); gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(14), 0, dp(14))
        setCompoundDrawablesRelative(null, null, icon("right", 18), null)
    }
    private fun preference(title: String, helper: String, enabled: Boolean, changed: (Boolean) -> Unit): View {
        val item = row().apply { setPadding(0, dp(10), 0, dp(10)); minimumHeight = dp(68) }
        val labels = column().apply {
            addView(label(title, 16f).apply { setPadding(0, 0, dp(8), dp(3)); typeface = Typeface.DEFAULT_BOLD })
            addView(label(helper, 14f, muted).apply { setPadding(0, 0, dp(8), 0) })
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        }
        item.addView(labels, LinearLayout.LayoutParams(0, -2, 1f))
        item.addView(Switch(this).apply {
            contentDescription = "$title. $helper"; minHeight = dp(48); minimumWidth = dp(48)
            isChecked = enabled
            trackDrawable = GradientDrawable().apply { setSize(dp(44), dp(26)); cornerRadius = dp(13).toFloat(); setColor(accent) }
            thumbDrawable = GradientDrawable().apply { shape = GradientDrawable.OVAL; setSize(dp(22), dp(22)); setColor(ink) }
            splitTrack = false
            thumbTintList = ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()), intArrayOf(ink, muted))
            trackTintList = ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()), intArrayOf(accent, RemoteColors.edge))
            setOnCheckedChangeListener { _, checked -> changed(checked) }
        })
        item.setOnClickListener { (item.getChildAt(1) as Switch).toggle() }
        return item
    }
    private fun showSettings() {
        screen = "settings"
        clearScreen()
        body.addView(label("Settings", 28f).apply { typeface = Typeface.DEFAULT_BOLD; accessibilityHeadingCompat() })
        section("Your TVs")
        val televisions = group()
        store.devices().forEach { device ->
            val item = row().apply { minimumHeight = dp(64) }
            val text = column()
            text.addView(label(device.name, 17f).apply { typeface = Typeface.DEFAULT_BOLD; setPadding(0, dp(12), 0, dp(2)) })
            val status = label("", 14f, muted).apply { setPadding(0, 0, 0, dp(12)); accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE }
            tvStatuses.add(device.id to status); text.addView(status)
            item.addView(text, LinearLayout.LayoutParams(0, -2, 1f))
            televisions.groupRow(item)
        }
        if (store.devices().isEmpty()) televisions.groupRow(label("No TVs saved yet", 16f, muted))
        body.addView(televisions)
        body.addView(textAction("Manage TVs") { showDevices() })
        section("Preferences")
        body.addView(group().apply {
            groupRow(preference("Button vibration", "Subtle feedback when you tap", store.haptics) { store.haptics = it; if (it) vibration.tap() })
            groupRow(preference("Automatic keyboard", "Opens for text boxes on your TV", store.automaticKeyboard) {
                store.automaticKeyboard = it; suppressedKeyboardScope = null; controller.keyboard(it)
            })
        })
        section("Help")
        body.addView(group().apply {
            groupRow(settingsLink("Connection help") { help() })
        })
        body.addView(View(this), LinearLayout.LayoutParams(-1, dp(12), 1f))
        body.addView(label("Local Wi-Fi · No account · No cloud", 13f, muted).apply { gravity = Gravity.CENTER; setPadding(0, dp(8), 0, dp(14)) })
        body.addView(backButton("Back to remote") { showRemote() })
        updateState(controller.state)
    }
    private fun backButton(title: String, action: () -> Unit) = button(title, action = action).apply {
            background = buttonBackground(RemoteColors.navy)
            (background as RippleDrawable).getDrawable(0)?.let { (it as? GradientDrawable)?.setStroke(dp(1), accent) }
            setCompoundDrawablesRelative(icon("back"), null, null, null)
    }
    private fun keyboardScope(state: RemoteState): String? = state.textTarget?.takeIf { state.connected }?.let {
        "${state.deviceId}:${state.sessionId}:${it.fieldId}:${it.focusVersion}"
    }
    private fun showKeyboard() {
        val device = selected() ?: return
        if (keyboardDialog != null) return
        cancelPresses()
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
        val form = column().apply { setPadding(dp(20), dp(16), dp(20), dp(12)) }
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
        clearScreen()
        body.add(label("Your televisions.", 28f).apply { typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL) })
        body.add(backButton("Back to settings") { showSettings() })
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
        body.add(label("Uses the unofficial Android TV remote protocol. A Google TV update may affect compatibility. Pairing keys stay protected on this phone. No analytics or sign-in.", 14f, muted))
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
        val form = column().apply { setPadding(dp(20), dp(16), dp(20), dp(12)) }
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
        val form = column().apply { setPadding(dp(24), dp(16), dp(24), dp(12)); add(input) }
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
    private fun help() {
        AlertDialog.Builder(this).setTitle("Getting connected")
            .setMessage("1. Wake the Chromecast with its physical remote.\n\n2. Put this phone and the Chromecast on the same home Wi-Fi. Avoid guest Wi-Fi and turn off a VPN if it blocks local devices.\n\n3. Search again. If discovery fails, find the TV's IP address in Google TV Settings → Network & Internet and add it manually.\n\n4. If the TV was reset or its identity changed, pair it again.\n\n5. Check Android TV Remote Service is installed and up to date. You do not need to turn on debugging.\n\nPairings are separate on each phone. Removing a TV deletes its credentials here; revoke old phone access in the TV settings if needed.\n\nUses an unofficial local protocol. Google TV updates may affect compatibility.")
            .setPositiveButton("Got it", null).show()
    }
    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    @Deprecated("Uses the platform back callback for API 26 compatibility")
    @Suppress("DEPRECATION")
    override fun onBackPressed() { when (screen) { "remote" -> super.onBackPressed(); "settings" -> showRemote(); else -> showSettings() } }
}
