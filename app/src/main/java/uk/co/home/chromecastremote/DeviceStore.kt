package uk.co.home.chromecastremote

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import uk.co.home.chromecastremote.protocol.Identity
import java.util.UUID

data class TvDevice(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val host: String,
    val serviceName: String = "",
    val port: Int = 6466,
    val fingerprint: String = "",
    val identityVersion: Int = Identity.CURRENT_VERSION
) { val paired get() = fingerprint.isNotEmpty() }

class DeviceStore(context: Context) {
    // Keep the original storage name so app-name changes retain saved TVs and preferences.
    private val prefs = context.getSharedPreferences("home_remote", Context.MODE_PRIVATE)
    init {
        if (!prefs.getBoolean("editable_favourites_v1", false)) {
            // Preserve buttons from 0.2.0, including its implicit defaults. New TVs start empty.
            devices().forEach { device ->
                if (!prefs.contains("favourites_${device.id}")) favourites(device.id, FavouriteApps.all)
            }
            prefs.edit().putBoolean("editable_favourites_v1", true).apply()
        }
    }
    var selected: String
        get() = prefs.getString("selected", "").orEmpty()
        set(value) { prefs.edit().putString("selected", value).apply() }
    var haptics: Boolean
        get() = prefs.getBoolean("haptics", true)
        set(value) { prefs.edit().putBoolean("haptics", value).apply() }
    var automaticKeyboard: Boolean
        get() = prefs.getBoolean("automatic_keyboard", true)
        set(value) { prefs.edit().putBoolean("automatic_keyboard", value).apply() }
    fun devices(): List<TvDevice> = runCatching {
        val array = JSONArray(prefs.getString("devices", "[]"))
        (0 until array.length()).map { i -> array.getJSONObject(i).let {
            TvDevice(it.getString("id"), it.getString("name"), it.getString("host"), it.optString("service"),
                it.optInt("port", 6466), it.optString("pin"), it.optInt("identityVersion", 1))
        } }
    }.getOrDefault(emptyList())
    fun save(device: TvDevice) {
        val current = devices().toMutableList()
        val index = current.indexOfFirst { it.id == device.id }
        if (index >= 0) current[index] = device else { require(current.size < 2); current.add(device) }
        write(current)
        if (selected.isEmpty()) selected = device.id
    }
    fun remove(id: String) {
        write(devices().filterNot { it.id == id })
        if (selected == id) selected = devices().firstOrNull()?.id.orEmpty()
        prefs.edit().remove("tests_$id").apply()
        prefs.edit().remove("favourites_$id").apply()
    }
    private fun write(devices: List<TvDevice>) {
        val array = JSONArray()
        devices.forEach { d -> array.put(JSONObject().put("id", d.id).put("name", d.name).put("host", d.host)
            .put("service", d.serviceName).put("port", d.port).put("pin", d.fingerprint)
            .put("identityVersion", d.identityVersion)) }
        prefs.edit().putString("devices", array.toString()).apply()
    }
    fun favourites(id: String): List<FavouriteApp> {
        val stored = prefs.getString("favourites_$id", null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(stored)
            (0 until array.length()).mapNotNull { index ->
                val json = array.optJSONObject(index)
                if (json == null) FavouriteApps.find(array.optString(index))
                else runCatching {
                    val app = FavouriteApp(json.getString("id"), json.getString("name"), json.getString("link"))
                    app.takeIf { it.id.isNotBlank() && it.name.isNotBlank() && FavouriteApps.validLink(it.link) }
                }.getOrNull()
            }.distinctBy { it.id }.take(12)
        }.getOrDefault(emptyList())
    }
    fun favourites(id: String, apps: List<FavouriteApp>) {
        require(apps.size <= 12)
        val array = JSONArray()
        apps.distinctBy { it.id }.forEach { app ->
            require(app.id.isNotBlank() && app.name.isNotBlank() && app.name.length <= 40 && FavouriteApps.validLink(app.link))
            array.put(JSONObject().put("id", app.id).put("name", app.name).put("link", app.link))
        }
        prefs.edit().putString("favourites_$id", array.toString()).apply()
    }
}
