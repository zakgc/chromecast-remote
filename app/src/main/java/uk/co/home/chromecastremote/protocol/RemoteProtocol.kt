package uk.co.home.chromecastremote.protocol

import uk.co.home.chromecastremote.BuildConfig
import uk.co.home.chromecastremote.protocol.Wire.nested
import uk.co.home.chromecastremote.protocol.Wire.num
import uk.co.home.chromecastremote.protocol.Wire.string
import java.io.IOException

/** Only ping, keys and volume metadata are negotiated; no keyboard, voice or app access. */
class RemoteProtocol {
    var features = 0; private set
    @Volatile var ready = false; private set
    var deviceDescription = ""; private set
    private var configured = false
    fun receive(data: ByteArray): ByteArray? {
        val fields = Wire.parse(data)
        return when {
            fields.any { it.number == 1 } -> {
                val config = fields.nested(1)
                features = config.num(1) and (1 or 2 or 64)
                if (features and 2 == 0) throw IOException("This device does not offer navigation controls")
                val info = config.nested(2)
                deviceDescription = listOf(info.string(2), info.string(1), info.string(6)).filter { it.isNotEmpty() }.joinToString(" · ")
                configured = true
                Wire.bytes(1, Wire.message(Wire.number(1, features), Wire.bytes(2, Wire.message(
                    Wire.number(3, 1), Wire.text(4, "1"), Wire.text(5, "atvremote"), Wire.text(6, BuildConfig.VERSION_NAME)
                ))))
            }
            fields.any { it.number == 2 } -> {
                if (!configured) throw IOException("Remote activated without configuration")
                // SetActive is the setup acknowledgement. Field 40 is optional power telemetry.
                // The controller writes this reply before publishing Connected or allowing keys.
                ready = true
                Wire.bytes(2, Wire.number(1, features))
            }
            fields.any { it.number == 8 } -> Wire.bytes(9, Wire.number(1, fields.nested(8).num(1)))
            fields.any { it.number == 40 } -> {
                if (!configured) throw IOException("Remote started without configuration")
                null
            }
            fields.any { it.number == 3 } -> throw IOException("The Chromecast rejected a remote command")
            else -> null
        }
    }
    companion object {
        fun key(code: Int) = Wire.bytes(10, Wire.message(Wire.number(1, code), Wire.number(2, 3)))
    }
}
