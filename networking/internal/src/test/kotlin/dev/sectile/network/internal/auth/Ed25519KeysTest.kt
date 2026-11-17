package dev.sectile.network.internal.auth

import dev.sectile.network.internal.auth.jwt.Ed25519Keys
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64

/**
 * Tests for the Ed25519 public key built from the JWKS.
 */
class Ed25519KeysTest {

    /** Real key published by the Session Service (embedded JWKS of the original server). */
    private val jwkX = "z9xeEbGzyV3RL_hh9dVbM9ik0vstBisICESg4WD3VCc"

    /**
     * The JCA public key is returned as SubjectPublicKeyInfo: the raw 32-byte key is
     * the last bytes of the DER.
     */
    private fun rawKey(publicKey: java.security.PublicKey): ByteArray = publicKey.encoded.copyOfRange(publicKey.encoded.size - 32, publicKey.encoded.size)

    @Test
    fun `the key keeps the encoding of the jwk`() {
        val key = Ed25519Keys.fromJwkX(jwkX)
        assertNotNull(key)

        assertArrayEquals(Base64.getUrlDecoder().decode(jwkX), rawKey(key!!))
    }

    @Test
    fun `a key built from the jwk verifies a signature made with the matching private key`() {
        // Generate a key pair, derive its x coordinate (as in the JWKS) and check
        // that the reconstructed key verifies the signature.
        val generator = KeyPairGenerator.getInstance("Ed25519")
        val pair = generator.generateKeyPair()

        // The `x` field of an OKP JWK is the raw key from RFC 8032.
        val x = Base64.getUrlEncoder().withoutPadding().encodeToString(rawKey(pair.public))
        val rebuilt = Ed25519Keys.fromJwkX(x)
        assertNotNull(rebuilt)

        val signer = Signature.getInstance("Ed25519")
        signer.initSign(pair.private)
        signer.update("hola".toByteArray())
        val signature = signer.sign()

        val verifier = Signature.getInstance("Ed25519")
        verifier.initVerify(rebuilt)
        verifier.update("hola".toByteArray())
        assertTrue(verifier.verify(signature))
    }

    @Test
    fun `a key built from the jwk rejects a signature made with another key`() {
        val generator = KeyPairGenerator.getInstance("Ed25519")
        val pair = generator.generateKeyPair()
        val other = generator.generateKeyPair()

        val x = Base64.getUrlEncoder().withoutPadding().encodeToString(rawKey(pair.public))
        val rebuilt = Ed25519Keys.fromJwkX(x)!!

        val signer = Signature.getInstance("Ed25519")
        signer.initSign(other.private)
        signer.update("hola".toByteArray())
        val signature = signer.sign()

        val verifier = Signature.getInstance("Ed25519")
        verifier.initVerify(rebuilt)
        verifier.update("hola".toByteArray())
        assertEquals(false, verifier.verify(signature))
    }

    @Test
    fun `invalid values are rejected`() {
        assertNull(Ed25519Keys.fromJwkX("no-es-base64!!"))
        assertNull(Ed25519Keys.fromJwkX(Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(16))))
    }
}
