package uk.co.home.chromecastremote.protocol

import org.junit.Assert.*
import org.junit.Test
import uk.co.home.chromecastremote.protocol.Wire.num
import uk.co.home.chromecastremote.protocol.Wire.nested
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPublicKey

class ProtocolTest {
    @Test fun fragmentedAndCoalescedFramesAreReadExactly() {
        val first = ByteArray(400) { (it % 251).toByte() }
        val second = byteArrayOf(1, 2, 3)
        val stream = ByteArrayOutputStream().apply { Wire.writeFrame(this, first); Wire.writeFrame(this, second) }
        val fragmented = object : InputStream() {
            val source = ByteArrayInputStream(stream.toByteArray())
            override fun read() = source.read()
            override fun read(b: ByteArray, off: Int, len: Int) = source.read(b, off, minOf(1, len))
        }
        assertArrayEquals(first, Wire.readFrame(fragmented))
        assertArrayEquals(second, Wire.readFrame(fragmented))
        assertThrows(EOFException::class.java) { Wire.readFrame(fragmented) }
    }
    @Test fun malformedLengthsAndTruncationAreRejected() {
        assertThrows(IOException::class.java) { Wire.readFrame(ByteArrayInputStream(Wire.encodeVarint(Wire.MAX_FRAME.toLong() + 1))) }
        assertThrows(IOException::class.java) { Wire.readFrame(ByteArrayInputStream(ByteArray(11) { 0x80.toByte() })) }
        assertThrows(EOFException::class.java) { Wire.readFrame(ByteArrayInputStream(byteArrayOf(3, 1))) }
        assertThrows(IOException::class.java) { Wire.parse(byteArrayOf(0)) }
        assertThrows(IOException::class.java) { Wire.parse(byteArrayOf(10) + Wire.encodeVarint(Long.MAX_VALUE)) }
    }
    @Test fun unknownFieldsDoNotBreakKnownFields() {
        val data = Wire.message(Wire.number(1, 200), Wire.bytes(888, byteArrayOf(7, 8)), Wire.number(2, 2))
        val result = Wire.parse(data)
        assertEquals(200, result.num(1)); assertEquals(2, result.num(2))
    }
    @Test fun keyEventUsesShortPressAndCorrectField() {
        val fields = Wire.parse(RemoteProtocol.key(23)).nested(10)
        assertEquals(23, fields.num(1)); assertEquals(3, fields.num(2))
    }
    @Test fun heldSelectUsesSeparatePressAndReleaseEvents() {
        val press = Wire.parse(RemoteProtocol.key(23, RemoteProtocol.START_LONG)).nested(10)
        val release = Wire.parse(RemoteProtocol.key(23, RemoteProtocol.END_LONG)).nested(10)
        assertEquals(23, press.num(1)); assertEquals(1, press.num(2))
        assertEquals(23, release.num(1)); assertEquals(2, release.num(2))
    }
    @Test fun handshakeNegotiatesOnlySupportedFeaturesAndAnswersPing() {
        val protocol = RemoteProtocol()
        assertFalse(protocol.ready)
        val reply = protocol.receive(Wire.bytes(1, Wire.number(1, 1023)))!!
        assertEquals(579, Wire.parse(reply).nested(1).num(1))
        assertFalse(protocol.ready)
        val active = protocol.receive(Wire.bytes(2, Wire.number(1, 1023)))!!
        assertEquals(579, Wire.parse(active).nested(2).num(1))
        assertTrue(protocol.ready)
        // Power telemetry may report false while asleep; it does not change channel readiness.
        protocol.receive(Wire.bytes(40, Wire.number(1, 0)))
        assertTrue(protocol.ready)
        val ping = protocol.receive(Wire.bytes(8, Wire.number(1, 300)))!!
        assertEquals(300, Wire.parse(ping).nested(9).num(1))
    }
    @Test fun unsupportedNavigationAndInvalidHandshakeFailClosed() {
        assertThrows(IOException::class.java) { RemoteProtocol().receive(Wire.bytes(1, Wire.number(1, 1))) }
        assertThrows(IOException::class.java) { RemoteProtocol().receive(Wire.bytes(40, byteArrayOf())) }
        assertThrows(IOException::class.java) { RemoteProtocol().receive(Wire.bytes(2, byteArrayOf())) }
        assertThrows(IOException::class.java) { RemoteProtocol().receive(Wire.bytes(3, byteArrayOf())) }
    }
    @Test fun staleOrDelayedCommandsCannotCrossDeviceSwitches() {
        val gate = CommandGate()
        val livingRoom = gate.invalidate()
        assertTrue(gate.accepts(livingRoom, 1000, 1010))
        val bedroom = gate.invalidate()
        assertFalse(gate.accepts(livingRoom, 1000, 1011))
        assertTrue(gate.accepts(bedroom, 1011, 1012))
        assertFalse(gate.accepts(bedroom, 1011, 1400))
        gate.invalidate() // app backgrounded or connection closed
        assertFalse(gate.accepts(bedroom, 1011, 1020))
    }
    @Test fun secretAuthenticatesBothCertificatesAndAcceptsHexLetters() {
        val generator = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }
        val client = generator.generateKeyPair().public as RSAPublicKey
        val server = generator.generateKeyPair().public as RSAPublicKey
        val nonce = byteArrayOf(0xab.toByte(), 0xcd.toByte())
        val digest = PairingSecret.digest(client, server, nonce)
        val code = "%02XABCD".format(digest[0].toInt() and 255)
        assertArrayEquals(digest, PairingSecret.verify(client, server, code.lowercase()))
        val badChecksum = "%02XABCD".format((digest[0].toInt() + 1) and 255)
        assertThrows(IllegalArgumentException::class.java) { PairingSecret.verify(client, server, badChecksum) }
        assertThrows(IllegalArgumentException::class.java) { PairingSecret.verify(client, server, "12345Z") }
        assertFalse(digest.contentEquals(PairingSecret.digest(server, client, nonce)))
    }
    @Test fun rsaIntegersAreUnsignedWithNoSignPadding() {
        assertArrayEquals(byteArrayOf(0x80.toByte()), PairingSecret.unsigned(BigInteger("80", 16)))
        assertArrayEquals(byteArrayOf(1, 0, 1), PairingSecret.unsigned(BigInteger.valueOf(65537)))
    }
    @Test fun navigationBecomesReadyOnActivationWithoutPowerTelemetry() {
        val protocol = RemoteProtocol()
        // TV offers exactly the capabilities requested by this app: ping, keys, volume; no power.
        protocol.receive(byteArrayOf(0x0a, 0x02, 0x08, 0x43))
        assertFalse(protocol.ready)
        val activationReply = protocol.receive(byteArrayOf(0x12, 0x00))!!
        assertArrayEquals(byteArrayOf(0x12, 0x02, 0x08, 0x43), activationReply)
        assertTrue("SetActive completes setup; the unrequested power feature will never send field 40", protocol.ready)
        repeat(4) { sequence ->
            val response = protocol.receive(Wire.bytes(8, Wire.number(1, sequence)))!!
            assertEquals(sequence, Wire.parse(response).nested(9).num(1))
            assertTrue(protocol.ready)
        }
    }
    @Test fun powerStatusAloneCannotActivateARemote() {
        val protocol = RemoteProtocol()
        protocol.receive(Wire.bytes(1, Wire.number(1, 67)))
        protocol.receive(Wire.bytes(40, Wire.number(1, 1)))
        assertFalse("Power status is telemetry, not the remote activation acknowledgement", protocol.ready)
    }
}
