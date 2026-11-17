package dev.sectile.network.internal.auth.jwt

import java.math.BigInteger
import java.security.KeyFactory
import java.security.PublicKey
import java.security.spec.EdECPoint
import java.security.spec.EdECPublicKeySpec
import java.security.spec.NamedParameterSpec
import java.util.Base64

/**
 * Construction of Ed25519 public keys from a JWK.
 *
 * The `x` field of an OKP JWK is the raw public key from RFC 8032: the `y` coordinate in
 * little-endian with the parity bit of `x` in the highest bit of the last byte.
 *
 * The JCA exposes the point as `EdECPoint(xOdd, y)`, so all that is needed is to undo the
 * little-endian and keep the parity; there is no need to decompress the point.
 */
object Ed25519Keys {

    /**
     * Builds the Ed25519 public key from the `x` coordinate of an OKP JWK.
     *
     * @param x base64url coordinate (32 bytes).
     * @return the public key, or `null` if the value is not valid.
     */
    fun fromJwkX(x: String): PublicKey? {
        val bytes = try {
            Base64.getUrlDecoder().decode(x)
        } catch (_: IllegalArgumentException) {
            return null
        }
        return fromEncoded(bytes)
    }

    /**
     * Builds the Ed25519 public key from its raw encoding (32 bytes).
     */
    fun fromEncoded(bytes: ByteArray): PublicKey? {
        if (bytes.size != 32) return null

        // Parity bit of x (bit 255 of the encoding).
        val xOdd = (bytes[31].toInt() and 0x80) != 0

        // y in little-endian, without the parity bit.
        val yBytes = bytes.copyOf()
        yBytes[31] = (yBytes[31].toInt() and 0x7F).toByte()
        val y = BigInteger(1, yBytes.reversedArray())

        return try {
            val point = EdECPoint(xOdd, y)
            val spec = EdECPublicKeySpec(NamedParameterSpec.ED25519, point)
            KeyFactory.getInstance("Ed25519").generatePublic(spec)
        } catch (_: Exception) {
            null
        }
    }
}
