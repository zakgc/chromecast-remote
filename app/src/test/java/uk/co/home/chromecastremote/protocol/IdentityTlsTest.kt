package uk.co.home.chromecastremote.protocol

import android.security.keystore.KeyProperties
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Exercises the production key-generation policy, not an emulated Keystore/TLS handshake. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 35])
class IdentityTlsTest {
    @Test fun tlsCanSignPrehashedCertificateVerifyData() {
        val spec = Identity.keySpec("tls-test", "test-device")
        assertTrue("Conscrypt requires DIGEST_NONE because TLS already hashes the handshake", spec.digests.contains(KeyProperties.DIGEST_NONE))
        assertTrue(spec.signaturePaddings.contains(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1))
        assertTrue(spec.signaturePaddings.contains(KeyProperties.SIGNATURE_PADDING_RSA_PSS))
    }
    @Test fun tls13CanApplyItsOwnPssEncodingBeforeTheKeystoreOperation() {
        val spec = Identity.keySpec("tls-test", "test-device")
        assertTrue("Conscrypt uses raw RSA for an already PSS-encoded TLS signature", spec.encryptionPaddings.contains(KeyProperties.ENCRYPTION_PADDING_NONE))
        assertEquals(KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY, spec.purposes)
        assertEquals(2048, spec.keySize)
        assertFalse(spec.isUserAuthenticationRequired)
    }
    @Test fun correctedKeysUseADifferentAliasWithoutOverwritingLegacyPairings() {
        assertEquals("home_remote_test-device", Identity.aliasFor("test-device", 1))
        assertEquals("home_remote_tls2_test-device", Identity.aliasFor("test-device", Identity.CURRENT_VERSION))
        assertNotEquals(Identity.aliasFor("test-device", 1), Identity.aliasFor("test-device", Identity.CURRENT_VERSION))
        assertThrows(IllegalArgumentException::class.java) { Identity.aliasFor("test-device", 999) }
    }
}
