package uk.co.home.chromecastremote.protocol

import java.math.BigInteger
import java.security.MessageDigest
import java.security.interfaces.RSAPublicKey

object PairingSecret {
    fun unsigned(value: BigInteger): ByteArray = value.toByteArray().let { if (it.size > 1 && it[0] == 0.toByte()) it.copyOfRange(1, it.size) else it }
    fun digest(client: RSAPublicKey, server: RSAPublicKey, nonce: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(
        unsigned(client.modulus) + unsigned(client.publicExponent) + unsigned(server.modulus) + unsigned(server.publicExponent) + nonce
    )
    fun verify(client: RSAPublicKey, server: RSAPublicKey, raw: String): ByteArray {
        val code = raw.trim()
        require(code.matches(Regex("[0-9a-fA-F]{6}"))) { "Enter all six letters or numbers shown on the TV (0–9, A–F)." }
        val result = digest(client, server, byteArrayOf(code.substring(2, 4).toInt(16).toByte(), code.substring(4, 6).toInt(16).toByte()))
        require((result[0].toInt() and 255) == code.substring(0, 2).toInt(16)) { "That code does not match this TV. Check the screen and try again." }
        return result
    }
}
