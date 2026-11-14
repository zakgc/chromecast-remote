package uk.co.home.chromecastremote.protocol

import org.junit.Assert.*
import org.junit.Test
import uk.co.home.chromecastremote.protocol.Wire.nested
import uk.co.home.chromecastremote.protocol.Wire.num
import uk.co.home.chromecastremote.protocol.Wire.string

class KeyboardProtocolTest {
    @Test fun signedEditorMetadataDoesNotInterruptTheRemoteConnection() {
        val protocol = RemoteProtocol(true)
        protocol.receive(Wire.bytes(1, Wire.number(1, 583)))
        protocol.receive(Wire.bytes(2, byteArrayOf()))
        // Encode the protobuf int32 wire form independently of our encoder:
        // IME_FLAG_FORCE_ASCII | SEARCH and unavailable (-1) selection offsets.
        val signedOptions = byteArrayOf(0x18, 0x83.toByte(), 0x80.toByte(), 0x80.toByte(), 0x80.toByte(),
            0xf8.toByte(), 0xff.toByte(), 0xff.toByte(), 0xff.toByte(), 0xff.toByte(), 0x01)
        val unavailable = ByteArray(9) { 0xff.toByte() } + byteArrayOf(0x01)
        protocol.receive(Wire.bytes(20, Wire.message(
            Wire.bytes(1, Wire.message(Wire.number(1, 17), Wire.number(2, 1), signedOptions)),
            Wire.bytes(2, Wire.message(Wire.number(1, 9), Wire.text(2, ""),
                byteArrayOf(0x18) + unavailable, byteArrayOf(0x20) + unavailable))
        )))
        val target = protocol.textTarget()!!
        assertEquals(Int.MIN_VALUE or 3, target.imeOptions)
        assertEquals(0, target.start); assertEquals(0, target.end)
        assertTrue(protocol.ready)
        assertEquals(42, Wire.parse(protocol.receive(Wire.bytes(8, Wire.number(1, 42)))!!).nested(9).num(1))
        assertNotNull(protocol.replaceText(target, "search", 6))
    }
    private fun focus(id: Int = 17, text: String = "hello", start: Int = 5, end: Int = 5, inputType: Int = 1, options: Int = 3) = Wire.bytes(20, Wire.message(
        Wire.bytes(1, Wire.message(Wire.number(1, id), Wire.number(2, inputType), Wire.number(3, options))),
        Wire.bytes(2, Wire.message(Wire.number(1, 9), Wire.text(2, text), Wire.number(3, start), Wire.number(4, end)))
    ))
    private fun keyboard(): TvKeyboard = TvKeyboard().apply { receive(Wire.parse(focus())) }
    @Test fun selectionAndReplacementAreOneBatchBoundToTheFocusedField() {
        val keyboard = TvKeyboard().apply { receive(Wire.parse(focus(text = "hello world", start = 6, end = 11))) }
        val target = keyboard.target!!
        val batch = Wire.parse(keyboard.replace(target, "hello TV 😀", 11)).nested(21)
        assertEquals(17, batch.num(1)); assertEquals(9, batch.num(2))
        val edits = batch.filter { it.number == 3 }.map { Wire.parse(it.bytes!!) }
        assertEquals(0, edits[0].nested(1).num(2)); assertEquals(11, edits[0].nested(1).num(3))
        assertEquals(0, edits[1].nested(2).num(1)); assertEquals(11, edits[1].nested(2).num(2))
        assertEquals("hello TV 😀", edits[1].nested(2).string(3))
        assertEquals(11, keyboard.target!!.start) // The emoji is two Android UTF-16 code units.
    }
    @Test fun successiveEditsReplaceTheUpdatedFullFieldRange() {
        val keyboard = keyboard()
        keyboard.replace(keyboard.target!!, "hello one", 9)
        val oldTarget = keyboard.target!!
        val second = Wire.parse(keyboard.replace(oldTarget, "hello one two", 13)).nested(21)
        val replacement = Wire.parse(second.filter { it.number == 3 }[1].bytes!!).nested(2)
        assertEquals(0, replacement.num(1)); assertEquals(9, replacement.num(2))
        assertEquals(13, keyboard.target!!.length)
    }
    @Test fun oldFieldAndOldConnectionContextCannotBeUsedAfterFocusChanges() {
        val keyboard = keyboard()
        val old = keyboard.target!!
        keyboard.receive(Wire.parse(focus(id = 18)))
        assertThrows(IllegalArgumentException::class.java) { keyboard.replace(old, "stale", 0) }
        keyboard.receive(Wire.parse(focus(id = 17)))
        assertNotEquals(old.focusVersion, keyboard.target!!.focusVersion)
        assertThrows(IllegalArgumentException::class.java) { keyboard.submit(old) }
    }
    @Test fun externalTextOrSelectionChangesInvalidateAnAlreadyQueuedSubmit() {
        val keyboard = keyboard()
        val before = keyboard.target!!
        keyboard.receive(Wire.parse(Wire.bytes(22, Wire.bytes(2, Wire.message(
            Wire.number(1, 10), Wire.text(2, "changed"), Wire.number(3, 7), Wire.number(4, 7)
        )))))
        assertThrows(IllegalArgumentException::class.java) { keyboard.submit(before) }
        val updated = keyboard.target!!
        keyboard.receive(Wire.parse(Wire.bytes(23, Wire.message(Wire.number(2, 1), Wire.number(3, 2)))))
        assertThrows(IllegalArgumentException::class.java) { keyboard.submit(updated) }
    }
    @Test fun keyboardHideResetAndNonEditableAppMetadataClearTheTarget() {
        val keyboard = keyboard()
        keyboard.receive(Wire.parse(Wire.bytes(21, Wire.number(1, 0))))
        assertNull(keyboard.target)
        keyboard.receive(Wire.parse(focus()))
        keyboard.receive(Wire.parse(Wire.bytes(24, byteArrayOf())))
        assertNull(keyboard.target)
        keyboard.receive(Wire.parse(focus(inputType = 0)))
        assertNull(keyboard.target)
    }
    @Test fun submitUsesTheTvSearchActionAndHonoursItsNoEnterActionFlag() {
        val keyboard = keyboard()
        val batch = Wire.parse(keyboard.submit(keyboard.target!!)).nested(21)
        assertEquals(3, Wire.parse(batch.first { it.number == 3 }.bytes!!).nested(5).num(1))
        keyboard.receive(Wire.parse(focus(options = 3 or 0x40000000)))
        assertArrayEquals(RemoteProtocol.key(66), keyboard.submit(keyboard.target!!))
    }
    @Test fun oversizedTextIsRejectedWithoutChangingCursor() {
        val keyboard = keyboard()
        val before = keyboard.target!!
        assertThrows(IllegalArgumentException::class.java) { keyboard.replace(before, "x".repeat(TvKeyboard.MAX_TEXT + 1), 0) }
        assertEquals(before, keyboard.target)
    }
    @Test fun keyboardIsNegotiatedOnlyOnRequestAndNeverInventsCapabilities() {
        val ordinary = RemoteProtocol()
        ordinary.receive(Wire.bytes(1, Wire.number(1, 1023)))
        assertEquals(0, ordinary.features and RemoteProtocol.IME)
        val keyboard = RemoteProtocol(true)
        keyboard.receive(Wire.bytes(1, Wire.number(1, 1023)))
        assertEquals(583, keyboard.features)
        keyboard.receive(Wire.bytes(2, byteArrayOf()))
        keyboard.receive(focus())
        assertNotNull(keyboard.textTarget())
        assertNotNull(keyboard.replaceText(keyboard.textTarget()!!, "test", 4))
        val unsupported = RemoteProtocol(true)
        unsupported.receive(Wire.bytes(1, Wire.number(1, 3)))
        unsupported.receive(Wire.bytes(2, byteArrayOf()))
        unsupported.receive(focus())
        assertNull(unsupported.textTarget())
        assertThrows(IllegalStateException::class.java) { unsupported.replaceText(keyboard.textTarget()!!, "no", 2) }
    }
    @Test fun liveReplacementSupportsEditingAndDeletingTheEntireField() {
        val keyboard = keyboard()
        val expected = keyboard.target!!
        val batch = Wire.parse(keyboard.replace(expected, "new 😀", 2)).nested(21)
        val edits = batch.filter { it.number == 3 }.map { Wire.parse(it.bytes!!) }
        assertEquals(0, edits[0].nested(1).num(2)); assertEquals(5, edits[0].nested(1).num(3))
        assertEquals("new 😀", edits[1].nested(2).string(3))
        assertEquals(2, edits.last().nested(1).num(2))
        keyboard.replace(keyboard.target!!, "", 0)
        assertEquals("", keyboard.target!!.text); assertEquals(0, keyboard.target!!.length)
    }
    @Test fun aDelayedOwnEchoCannotRewindNewerPhoneTyping() {
        val keyboard = keyboard()
        val expected = keyboard.target!!
        keyboard.replace(expected, "first", 5)
        keyboard.replace(expected, "latest text", 11)
        keyboard.receive(Wire.parse(Wire.bytes(22, Wire.message(Wire.number(1, 1), Wire.bytes(2, Wire.message(
            Wire.number(1, 10), Wire.text(2, "first"), Wire.number(3, 5), Wire.number(4, 5)
        ))))))
        assertEquals("latest text", keyboard.target!!.text)
        assertEquals(11, keyboard.target!!.start)
        val batch = Wire.parse(keyboard.replace(expected, "latest text!", 12)).nested(21)
        assertEquals(10, batch.num(2)) // Use the newest TV counter, even when the caller's cursor snapshot is old.
    }
    @Test fun liveReplacementCannotMoveAcrossAFocusChange() {
        val keyboard = keyboard()
        val expected = keyboard.target!!
        keyboard.receive(Wire.parse(focus(id = 18)))
        assertThrows(IllegalArgumentException::class.java) { keyboard.replace(expected, "old draft", 0) }
        assertEquals(18, keyboard.target!!.fieldId)
    }
    @Test fun repeatingMetadataForTheSameEditorDoesNotCreateANewFocus() {
        val keyboard = keyboard()
        val version = keyboard.target!!.focusVersion
        keyboard.receive(Wire.parse(focus()))
        assertEquals(version, keyboard.target!!.focusVersion)
    }
}
