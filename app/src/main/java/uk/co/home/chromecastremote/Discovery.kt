package uk.co.home.chromecastremote

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import java.io.Closeable
import java.util.ArrayDeque

data class FoundTv(val name: String, val host: String, val port: Int)

@Suppress("DEPRECATION") // resolveService supports API 26; serialize requests to avoid resolver contention.
class Discovery(context: Context, private val found: (FoundTv) -> Unit, private val status: (String) -> Unit) : Closeable {
    private val nsd = context.getSystemService(NsdManager::class.java)
    private val wifi = context.applicationContext.getSystemService(WifiManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private var lock: WifiManager.MulticastLock? = null
    private var listener: NsdManager.DiscoveryListener? = null
    private var generation = 0
    private val queue = ArrayDeque<NsdServiceInfo>()
    private var resolving = false
    fun start() {
        close()
        val token = generation
        lock = wifi.createMulticastLock("home-remote-discovery").apply { setReferenceCounted(false); acquire() }
        val discovery = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(type: String) { main.post { if (token == generation) status("Looking on your home network…") } }
            override fun onServiceFound(info: NsdServiceInfo) { main.post { if (token == generation) { queue.add(info); resolveNext(token) } } }
            override fun onServiceLost(info: NsdServiceInfo) = Unit
            override fun onDiscoveryStopped(type: String) = Unit
            override fun onStartDiscoveryFailed(type: String, code: Int) { main.post { if (token == generation) { close(); status("Discovery unavailable ($code). Try again or add the TV by IP address.") } } }
            override fun onStopDiscoveryFailed(type: String, code: Int) = Unit
        }
        listener = discovery
        try { nsd.discoverServices("_androidtvremote2._tcp.", NsdManager.PROTOCOL_DNS_SD, discovery) }
        catch (_: Exception) { close(); status("Could not search this network. Try adding the TV by IP address.") }
        main.postDelayed({ if (token == generation) { close(); status("Search finished. If a TV is missing, wake it and search again.") } }, 20000)
    }
    private fun resolveNext(token: Int) {
        if (resolving || queue.isEmpty() || token != generation) return
        resolving = true
        try { nsd.resolveService(queue.removeFirst(), object : NsdManager.ResolveListener {
            override fun onResolveFailed(info: NsdServiceInfo, code: Int) { main.post { if (token == generation) { resolving = false; resolveNext(token) } } }
            override fun onServiceResolved(info: NsdServiceInfo) {
                main.post {
                    if (token != generation) return@post
                    resolving = false
                    info.host?.hostAddress?.let { if (info.port in 1..65534) found(FoundTv(info.serviceName, it, info.port)) }
                    resolveNext(token)
                }
            }
        }) } catch (_: Exception) {
            resolving = false
            status("A TV could not be resolved. Try searching again or add its IP address.")
            resolveNext(token)
        }
    }
    override fun close() {
        generation++
        main.removeCallbacksAndMessages(null)
        listener?.let { runCatching { nsd.stopServiceDiscovery(it) } }
        listener = null
        if (lock?.isHeld == true) lock?.release()
        lock = null
        queue.clear(); resolving = false
    }
}
