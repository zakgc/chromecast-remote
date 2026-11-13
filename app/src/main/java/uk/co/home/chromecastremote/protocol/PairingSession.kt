package uk.co.home.chromecastremote.protocol

import uk.co.home.chromecastremote.TvDevice
import uk.co.home.chromecastremote.protocol.Wire.num
import java.io.Closeable
import java.io.IOException
import java.security.cert.X509Certificate
import java.security.interfaces.RSAPublicKey
import javax.net.ssl.SSLSocket

class PairingSession(private val device: TvDevice) : Closeable {
    @Volatile private var socket: SSLSocket? = null
    @Volatile private var cancelled = false
    private lateinit var identity: Identity
    private lateinit var peer: X509Certificate
    fun begin() {
        // Keep any old identity intact until the replacement is paired and saved by the UI.
        identity = Identity(device.id, Identity.CURRENT_VERSION)
        val connection = identity.newSocket(null)
        socket = connection
        if (cancelled) { connection.close(); throw IOException("Pairing cancelled") }
        Identity.connect(connection, device.host, device.port + 1)
        peer = connection.session.peerCertificates.first() as X509Certificate
        exchange(10, Wire.message(Wire.text(1, "atvremote"), Wire.text(2, "Chromecast Remote · ${android.os.Build.MODEL}")), 11)
        val encoding = Wire.message(Wire.number(1, 3), Wire.number(2, 6))
        exchange(20, Wire.message(Wire.bytes(1, encoding), Wire.number(3, 1)), 20)
        exchange(30, Wire.message(Wire.bytes(1, encoding), Wire.number(2, 1)), 31)
    }
    fun finish(code: String): String {
        val secret = PairingSecret.verify(identity.certificate.publicKey as RSAPublicKey, peer.publicKey as RSAPublicKey, code)
        exchange(40, Wire.bytes(1, secret), 41)
        return Identity.fingerprint(peer)
    }
    private fun exchange(field: Int, content: ByteArray, expected: Int) {
        val connection = socket ?: throw IOException("Pairing closed")
        Wire.writeFrame(connection.outputStream, Wire.message(Wire.number(1, 2), Wire.number(2, 200), Wire.bytes(field, content)))
        val response = Wire.parse(Wire.readFrame(connection.inputStream))
        if (response.num(2) != 200) throw IOException("Pairing rejected (${response.num(2)}). Start pairing again and check the TV code.")
        if (response.none { it.number == expected }) throw IOException("Unexpected pairing response; this device may be incompatible.")
    }
    override fun close() { cancelled = true; runCatching { socket?.close() }; socket = null }
}
