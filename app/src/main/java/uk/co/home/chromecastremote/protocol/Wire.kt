package uk.co.home.chromecastremote.protocol

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.io.IOException

/** Small, bounded protobuf subset. Unknown fields are skipped for forward compatibility. */
object Wire {
    const val MAX_FRAME = 1024 * 1024
    data class Field(val number: Int, val value: Long = 0, val bytes: ByteArray? = null)
    fun number(field: Int, value: Int): ByteArray = encodeVarint((field shl 3).toLong()) + encodeVarint(value.toLong())
    fun bytes(field: Int, value: ByteArray): ByteArray = encodeVarint(((field shl 3) or 2).toLong()) + encodeVarint(value.size.toLong()) + value
    fun text(field: Int, value: String) = bytes(field, value.toByteArray(Charsets.UTF_8))
    fun message(vararg parts: ByteArray): ByteArray = parts.fold(byteArrayOf()) { result, part -> result + part }
    fun encodeVarint(value: Long): ByteArray {
        val out = ByteArrayOutputStream()
        var remaining = value
        // Protobuf int32 negatives are sign-extended to a ten-byte varint.
        while (remaining and -128L != 0L) { out.write((remaining.toInt() and 127) or 128); remaining = remaining ushr 7 }
        out.write(remaining.toInt())
        return out.toByteArray()
    }
    fun readVarint(input: InputStream): Long {
        var value = 0L
        for (shift in 0..63 step 7) {
            val byte = input.read()
            if (byte < 0) throw EOFException("Connection closed")
            if (shift == 63 && byte > 1) throw IOException("Varint overflow")
            value = value or ((byte and 127).toLong() shl shift)
            if (byte and 128 == 0) return value
        }
        throw IOException("Invalid varint")
    }
    private fun readExact(input: InputStream, length: Int): ByteArray {
        if (length !in 0..MAX_FRAME) throw IOException("Message too large")
        val data = ByteArray(length)
        var offset = 0
        while (offset < length) {
            val count = input.read(data, offset, length - offset)
            if (count < 0) throw EOFException("Incomplete message")
            offset += count
        }
        return data
    }
    fun readFrame(input: InputStream): ByteArray {
        val size = readVarint(input)
        if (size !in 0..MAX_FRAME.toLong()) throw IOException("Invalid frame size")
        return readExact(input, size.toInt())
    }
    fun writeFrame(output: OutputStream, data: ByteArray) {
        require(data.size <= MAX_FRAME)
        output.write(encodeVarint(data.size.toLong())); output.write(data); output.flush()
    }
    fun parse(data: ByteArray): List<Field> {
        val input = ByteArrayInputStream(data)
        val fields = mutableListOf<Field>()
        while (input.available() > 0) {
            val tag = readVarint(input)
            if (tag ushr 3 !in 1..536870911) throw IOException("Invalid field")
            val number = (tag ushr 3).toInt()
            when ((tag and 7).toInt()) {
                0 -> fields.add(Field(number, readVarint(input)))
                1 -> readExact(input, 8)
                2 -> {
                    val size = readVarint(input)
                    if (size !in 0..MAX_FRAME.toLong()) throw IOException("Invalid field length")
                    fields.add(Field(number, bytes = readExact(input, size.toInt())))
                }
                5 -> readExact(input, 4)
                else -> throw IOException("Unsupported wire type")
            }
        }
        return fields
    }
    fun List<Field>.num(field: Int) = firstOrNull { it.number == field }?.value?.toInt() ?: 0
    fun List<Field>.nested(field: Int) = firstOrNull { it.number == field }?.bytes?.let(::parse) ?: emptyList()
    fun List<Field>.string(field: Int) = firstOrNull { it.number == field }?.bytes?.toString(Charsets.UTF_8).orEmpty()
}
