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
    val volumeVerified: Boolean = false,
    val playbackVerified: Boolean = false,
    val identityVersion: Int = Identity.CURRENT_VERSION
) { val paired get() = fingerprint.isNotEmpty() }

class DeviceStore(context: Context) {
    private val prefs = context.getSharedPreferences("home_remote", Context.MODE_PRIVATE)
    var selected: String
        get() = prefs.getString("selected", "").orEmpty()
        set(value) { prefs.edit().putString("selected", value).apply() }
    var haptics: Boolean
        get() = prefs.getBoolean("haptics", true)
        set(value) { prefs.edit().putBoolean("haptics", value).apply() }
    fun devices(): List<TvDevice> = runCatching {
        val array = JSONArray(prefs.getString("devices", "[]"))
        (0 until array.length()).map { i -> array.getJSONObject(i).let {
            TvDevice(it.getString("id"), it.getString("name"), it.getString("host"), it.optString("service"),
                it.optInt("port", 6466), it.optString("pin"), it.optBoolean("volume"), it.optBoolean("playback"), it.optInt("identityVersion", 1))
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
    }
    private fun write(devices: List<TvDevice>) {
        val array = JSONArray()
        devices.forEach { d -> array.put(JSONObject().put("id", d.id).put("name", d.name).put("host", d.host)
            .put("service", d.serviceName).put("port", d.port).put("pin", d.fingerprint)
            .put("volume", d.volumeVerified).put("playback", d.playbackVerified).put("identityVersion", d.identityVersion)) }
        prefs.edit().putString("devices", array.toString()).apply()
    }
    fun testResults(id: String): JSONObject = runCatching { JSONObject(prefs.getString("tests_$id", "{}") ?: "{}") }.getOrDefault(JSONObject())
    fun testResult(id: String, name: String, value: String) {
        val json = testResults(id).put(name, value)
        prefs.edit().putString("tests_$id", json.toString()).apply()
    }
}
