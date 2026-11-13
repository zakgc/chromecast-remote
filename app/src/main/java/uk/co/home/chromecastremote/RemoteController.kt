package uk.co.home.chromecastremote

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import uk.co.home.chromecastremote.protocol.CommandGate
import uk.co.home.chromecastremote.protocol.Identity
import uk.co.home.chromecastremote.protocol.RemoteProtocol
import uk.co.home.chromecastremote.protocol.Wire
import java.io.Closeable
import java.util.concurrent.Executors
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLSocket

data class RemoteState(val deviceId: String = "", val connected: Boolean = false, val label: String = "Choose a device", val details: String = "")

class RemoteController(private val changed: (RemoteState) -> Unit) : Closeable {
    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newCachedThreadPool()
    private val writer = ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, ArrayBlockingQueue(8), ThreadPoolExecutor.DiscardPolicy())
    private val gate = CommandGate()
    @Volatile private var current: Connection? = null
    private var foreground = false
    private var network = true
    private var selected: TvDevice? = null
    var state = RemoteState(); private set
    private class Connection(val socket: SSLSocket, val token: Long) {
        val writeLock = Any()
        @Volatile var setupTimedOut = false
        fun write(data: ByteArray) = synchronized(writeLock) { Wire.writeFrame(socket.outputStream, data) }
    }
    fun resume(device: TvDevice?, lanAvailable: Boolean) { foreground = true; network = lanAvailable; select(device) }
    fun networkChanged(available: Boolean) {
        if (network == available) return
        network = available
        if (foreground) select(selected)
    }
    fun select(device: TvDevice?) {
        disconnect()
        selected = device
        if (!foreground) return
        when {
            device == null -> publish(RemoteState())
            !device.paired -> publish(RemoteState(device.id, label = "Pair this device in Devices"))
            !network -> publish(RemoteState(device.id, label = "Wi-Fi unavailable", details = "Connect to the same home network as your Chromecast."))
            else -> connect(device, gate.generation, 0)
        }
    }
    private fun connect(device: TvDevice, token: Long, attempt: Int) {
        if (token != gate.generation || !foreground) return
        publish(RemoteState(device.id, label = if (attempt == 0) "Connecting…" else "Reconnecting…"))
        val started = SystemClock.elapsedRealtime()
        io.execute {
            var connection: Connection? = null
            var secureConnection = false
            var announced = false
            try {
                val socket = Identity(device.id, device.identityVersion).newSocket(device.fingerprint)
                connection = Connection(socket, token)
                val active = connection
                // Register before connecting so switching or leaving the app cancels an in-flight socket.
                synchronized(this) {
                    if (token != gate.generation) { socket.close(); return@execute }
                    current = active
                }
                Identity.connect(socket, device.host, device.port)
                secureConnection = true
                socket.soTimeout = 16000
                val protocol = RemoteProtocol()
                main.postDelayed({
                    if (token == gate.generation && !protocol.ready) {
                        active.setupTimedOut = true
                        runCatching { socket.close() }
                    }
                }, 12000)
                while (token == gate.generation) {
                    val reply = protocol.receive(Wire.readFrame(socket.inputStream))
                    if (reply != null) active.write(reply)
                    if (protocol.ready && !announced) {
                        announced = true
                        main.post {
                            if (token == gate.generation) publish(RemoteState(device.id, true, "Connected", "${SystemClock.elapsedRealtime() - started} ms to connect · ${protocol.deviceDescription}"))
                        }
                    }
                }
            } catch (error: Exception) {
                val timedOut = connection?.setupTimedOut == true
                val failure = when {
                    timedOut -> RemoteState(device.id, label = "Remote setup timed out", details = "The TV accepted the encrypted connection, but its remote service did not finish starting within 12 seconds. Retrying automatically.")
                    announced -> RemoteState(device.id, label = "Connection interrupted", details = "The remote connection ended. Reconnecting automatically. ${error.message.orEmpty().take(160)}")
                    secureConnection -> RemoteState(device.id, label = "Remote setup failed", details = "The encrypted connection opened, but the remote did not finish starting. Retrying automatically. ${error.message.orEmpty().take(160)}")
                    else -> RemoteState(device.id, label = "Couldn’t open TV connection", details = "${error.message.orEmpty().take(160)}. Retrying automatically; your saved pairing is kept.")
                }
                main.post {
                    if (token == gate.generation && foreground) {
                        publish(failure)
                        val delay = minOf(30000L, 2000L shl minOf(attempt, 4))
                        main.postDelayed({ if (token == gate.generation && foreground && network) connect(device, token, attempt + 1) }, delay)
                    }
                }
            } finally { runCatching { connection?.socket?.close() } }
        }
    }
    fun send(key: Int) {
        if (!state.connected) return
        val connection = current ?: return
        val time = SystemClock.elapsedRealtime()
        writer.execute {
            if (!gate.accepts(connection.token, time, SystemClock.elapsedRealtime())) return@execute
            try { connection.write(RemoteProtocol.key(key)) }
            catch (_: Exception) { runCatching { connection.socket.close() } }
        }
    }
    private fun publish(value: RemoteState) { state = value; changed(value) }
    @Synchronized private fun disconnect() {
        gate.invalidate()
        main.removeCallbacksAndMessages(null)
        writer.queue.clear()
        runCatching { current?.socket?.close() }
        current = null
    }
    fun pause() { foreground = false; disconnect(); state = state.copy(connected = false, label = "Disconnected") }
    override fun close() { pause(); io.shutdownNow(); writer.shutdownNow() }
}
