package uk.co.home.chromecastremote.protocol

import android.annotation.SuppressLint
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.math.BigInteger
import java.net.InetSocketAddress
import java.net.Socket
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.Principal
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.Date
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509ExtendedKeyManager
import javax.net.ssl.X509TrustManager
import javax.security.auth.x500.X500Principal

class Identity(private val id: String, version: Int = CURRENT_VERSION) {
    private val alias = aliasFor(id, version)
    private val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    init {
        if (!store.containsAlias(alias)) {
            val generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, "AndroidKeyStore")
            generator.initialize(keySpec(alias, id))
            generator.generateKeyPair()
        }
    }
    val certificate get() = store.getCertificate(alias) as X509Certificate
    @SuppressLint("CustomX509TrustManager") // TVs use self-signed certs: code-authenticated pairing, exact certificate pin thereafter.
    fun newSocket(pin: String?): SSLSocket {
        val keyManager = object : X509ExtendedKeyManager() {
            override fun getClientAliases(keyType: String?, issuers: Array<out Principal>?) = arrayOf(alias)
            override fun chooseClientAlias(keyType: Array<out String>?, issuers: Array<out Principal>?, socket: Socket?) = alias
            override fun getCertificateChain(alias: String?) = arrayOf(certificate)
            override fun getPrivateKey(alias: String?) = store.getKey(this@Identity.alias, null) as PrivateKey
            override fun getServerAliases(keyType: String?, issuers: Array<out Principal>?) = null
            override fun chooseServerAlias(keyType: String?, issuers: Array<out Principal>?, socket: Socket?) = null
        }
        // Pairing authenticates the self-signed certificate with the code; all later sessions pin it.
        val trust = object : X509TrustManager {
            override fun getAcceptedIssuers() = emptyArray<X509Certificate>()
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) { throw CertificateException("Client mode only") }
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                val peer = chain?.firstOrNull() ?: throw CertificateException("No TV certificate")
                if (pin != null && pin != fingerprint(peer)) throw CertificateException("TV identity changed. Pair again from Devices.")
            }
        }
        return SSLContext.getInstance("TLS").apply { init(arrayOf(keyManager), arrayOf(trust), SecureRandom()) }
            .socketFactory.createSocket() as SSLSocket
    }
    companion object {
        const val CURRENT_VERSION = 2
        // Keep these aliases stable so app-name changes retain existing TV pairings.
        internal fun aliasFor(id: String, version: Int): String = when (version) {
            1 -> "home_remote_$id"
            CURRENT_VERSION -> "home_remote_tls2_$id"
            else -> throw IllegalArgumentException("Unsupported pairing identity version")
        }
        internal fun keySpec(alias: String, id: String): KeyGenParameterSpec =
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY)
                // Conscrypt hashes/encodes CertificateVerify itself, then delegates raw RSA to Keystore.
                // PKCS#1 needs DIGEST_NONE; TLS 1.3 PSS also needs the no-padding RSA operation.
                .setKeySize(2048).setDigests(KeyProperties.DIGEST_NONE, KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA384, KeyProperties.DIGEST_SHA512)
                .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1, KeyProperties.SIGNATURE_PADDING_RSA_PSS)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setCertificateSubject(X500Principal("CN=Chromecast Remote-$id"))
                .setCertificateSerialNumber(BigInteger(128, SecureRandom()))
                .setCertificateNotBefore(Date(System.currentTimeMillis() - 86400000L))
                .setCertificateNotAfter(Date(System.currentTimeMillis() + 10L * 365 * 86400000L)).build()
        fun fingerprint(cert: X509Certificate) = MessageDigest.getInstance("SHA-256").digest(cert.encoded).joinToString("") { "%02x".format(it) }
        fun forget(id: String) {
            KeyStore.getInstance("AndroidKeyStore").apply {
                load(null)
                deleteEntry(aliasFor(id, 1))
                deleteEntry(aliasFor(id, CURRENT_VERSION))
            }
        }
        fun connect(socket: SSLSocket, host: String, port: Int) {
            socket.enabledProtocols = socket.supportedProtocols.filter { it == "TLSv1.2" || it == "TLSv1.3" }.toTypedArray()
            socket.soTimeout = 10000
            socket.connect(InetSocketAddress(host, port), 5000)
            socket.startHandshake()
        }
    }
}
