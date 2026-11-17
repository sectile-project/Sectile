package dev.sectile.network.internal.stages

import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Creates the challenges used by the password stage.
 *
 * A challenge is a random blob the client hashes together with the password: the server never
 * receives the password itself, only its hash.
 */
internal object PasswordChallenge {

    /** Length of a challenge, in bytes. */
    const val LENGTH: Int = 32

    private val random = SecureRandom()

    /**
     * Creates a new challenge.
     *
     * @param password password of the server, or `null` when the server does not ask for one.
     * @return the challenge, or `null` when the server does not ask for a password.
     */
    fun create(password: String?): ByteArray? {
        if (password.isNullOrEmpty()) return null
        val challenge = ByteArray(LENGTH)
        random.nextBytes(challenge)
        return challenge
    }

    /**
     * Computes the response the client is expected to send: the SHA-256 of the challenge
     * followed by the UTF-8 password.
     */
    fun expectedHash(challenge: ByteArray, password: String): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(challenge)
        digest.update(password.toByteArray(Charsets.UTF_8))
        return digest.digest()
    }
}
