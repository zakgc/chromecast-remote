package uk.co.home.chromecastremote.protocol

import uk.co.home.chromecastremote.BuildConfig
import uk.co.home.chromecastremote.protocol.Wire.nested
import uk.co.home.chromecastremote.protocol.Wire.num
import uk.co.home.chromecastremote.protocol.Wire.string
import java.io.IOException

/** IME is requested for automatic keyboard mode or while the manual keyboard dialog is open. */
class RemoteProtocol(private val keyboardEnabled: Boolean = false) {
    var features = 0; private set
    @Volatile var ready = false; private set
    var deviceDescription = ""; private set
    private var configured = false
    private val keyboard = TvKeyboard()
    @Synchronized fun textTarget(): TvTextTarget? = keyboard.target
    @Synchronized fun replaceText(target: TvTextTarget, text: String, selection: Int): ByteArray {
        check(ready && features and IME != 0) { "The TV keyboard is not connected." }
        return keyboard.replace(target, text, selection)
    }
    @Synchronized fun submitText(target: TvTextTarget): ByteArray {
        check(ready && features and IME != 0) { "The TV keyboard is not connected." }
        return keyboard.submit(target)
    }
    @Synchronized
    fun receive(data: ByteArray): ByteArray? {
        val fields = Wire.parse(data)
        if (features and IME != 0) keyboard.receive(fields)
        return when {
            fields.any { it.number == 1 } -> {
                val config = fields.nested(1)
                features = config.num(1) and (1 or 2 or 64 or APP_LINKS or if (keyboardEnabled) IME else 0)
                if (features and 2 == 0) throw IOException("This device does not offer navigation controls")
                val info = config.nested(2)
                deviceDescription = listOf(info.string(2), info.string(1), info.string(6)).filter { it.isNotEmpty() }.joinToString(" · ")
                configured = true
                Wire.bytes(1, Wire.message(Wire.number(1, features), Wire.bytes(2, Wire.message(
                    Wire.number(3, 1), Wire.text(4, "1"), Wire.text(5, "Chromecast Remote"), Wire.text(6, BuildConfig.VERSION_NAME)
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
            fields.any { it.number == 3 } -> throw IOException("The TV rejected a command (code ${fields.nested(3).num(1)}). If an app link failed, check that the app is installed and up to date.")
            else -> null
        }
    }
    companion object {
        const val IME = 4
        const val APP_LINKS = 512
        const val START_LONG = 1
        const val END_LONG = 2
        const val SHORT = 3
        fun key(code: Int, direction: Int = SHORT) = Wire.bytes(10, Wire.message(Wire.number(1, code), Wire.number(2, direction)))
        fun appLink(url: String): ByteArray {
            require(url.isNotBlank() && url.length <= 2048)
            return Wire.bytes(90, Wire.text(1, url))
        }
    }
}
