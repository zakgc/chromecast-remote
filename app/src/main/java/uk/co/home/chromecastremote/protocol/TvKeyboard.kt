package uk.co.home.chromecastremote.protocol

import uk.co.home.chromecastremote.protocol.Wire.nested
import uk.co.home.chromecastremote.protocol.Wire.num
import uk.co.home.chromecastremote.protocol.Wire.string

data class TvTextTarget(
    val fieldId: Int, val counter: Int, val focusVersion: Long,
    val start: Int, val end: Int, val length: Int, val imeOptions: Int,
    val text: String = "", val inputType: Int = 1, val packageName: String = ""
)

/** Text exists only for the active editor session; it is never persisted or logged. */
class TvKeyboard {
    var target: TvTextTarget? = null; private set
    private var focusVersion = 0L
    fun receive(fields: List<Wire.Field>) {
        when {
            fields.any { it.number == 20 } -> {
                val field = fields.nested(20)
                val editor = field.nested(1)
                val status = field.nested(2)
                val old = target
                if (old == null || old.fieldId != editor.num(1) || old.inputType != editor.num(2) || old.packageName != editor.string(12)) focusVersion++
                target = if (editor.num(2) == 0 || editor.num(1) <= 0) null else fromStatus(
                    editor.num(1), editor.num(3), status, editor.num(2), editor.string(12)
                )
            }
            fields.any { it.number == 22 } -> {
                val old = target ?: return
                val update = fields.nested(22)
                val status = update.nested(2)
                if (status.isNotEmpty()) {
                    target = if (update.num(1) != 0) old.copy(counter = status.num(1))
                    else fromStatus(old.fieldId, old.imeOptions, status, old.inputType, old.packageName)
                }
            }
            fields.any { it.number == 23 } -> {
                val old = target ?: return
                val selection = fields.nested(23)
                target = old.copy(start = selection.num(2).coerceIn(0, old.length), end = selection.num(3).coerceIn(0, old.length))
            }
            fields.any { it.number == 24 } -> { focusVersion++; target = null }
            fields.any { it.number == 21 } && fields.nested(21).num(1) == 0 -> { focusVersion++; target = null }
        }
    }
    private fun fromStatus(id: Int, options: Int, status: List<Wire.Field>, inputType: Int, packageName: String): TvTextTarget {
        val text = status.string(2).take(MAX_TEXT)
        val length = text.length // Android cursor offsets count UTF-16 code units.
        return TvTextTarget(id, status.num(1), focusVersion, status.num(3).coerceIn(0, length), status.num(4).coerceIn(0, length), length, options, text, inputType, packageName)
    }
    fun replace(expected: TvTextTarget, text: String, selection: Int): ByteArray {
        val current = target ?: throw IllegalArgumentException("The TV text box is no longer focused.")
        require(current.fieldId == expected.fieldId && current.focusVersion == expected.focusVersion) { "The TV text box changed." }
        require(text.length <= MAX_TEXT)
        val cursor = selection.coerceIn(0, text.length)
        val message = batch(current,
            Wire.bytes(1, Wire.message(Wire.number(2, 0), Wire.number(3, current.length))),
            Wire.bytes(2, Wire.message(Wire.number(1, 0), Wire.number(2, current.length), Wire.text(3, text))),
            Wire.bytes(1, Wire.message(Wire.number(2, cursor), Wire.number(3, cursor)))
        )
        target = current.copy(text = text, length = text.length, start = cursor, end = cursor)
        return message
    }
    fun submit(expected: TvTextTarget): ByteArray {
        require(target == expected) { "The TV text field changed. Try again." }
        val action = expected.imeOptions and 0xff
        return if (action in 2..7 && expected.imeOptions and 0x40000000 == 0) {
            batch(expected, Wire.bytes(5, Wire.number(1, action)))
        } else RemoteProtocol.key(66)
    }
    private fun batch(target: TvTextTarget, vararg edits: ByteArray) = Wire.bytes(21, Wire.message(
        Wire.number(1, target.fieldId), Wire.number(2, target.counter), *edits.map { Wire.bytes(3, it) }.toTypedArray()
    ))
    companion object { const val MAX_TEXT = 4096 }
}
