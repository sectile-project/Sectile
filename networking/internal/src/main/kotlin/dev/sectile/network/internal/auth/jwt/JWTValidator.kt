package dev.sectile.network.internal.auth.jwt

import dev.sectile.kernel.config.ConfigSection
import dev.sectile.kernel.config.ConfigServiceRegistry
import dev.sectile.kernel.logger.Logger
import java.security.Signature
import java.security.cert.X509Certificate
import java.time.Instant
import java.util.Base64

/**
 * Claims from an identity token (the one sent by the client in `Connect`).
 */
class IdentityTokenClaims(
    val issuer: String?,
    val subject: String?,
    val username: String?,
    val skin: String?,
    val entitlements: List<String>,
    val issuedAt: Long?,
    val expiresAt: Long?,
    val notBefore: Long?,
    val scope: String?,
) {
    /** Token scopes, separated by spaces. */
    val scopes: List<String>
        get() = scope?.split(' ')?.filter { it.isNotEmpty() } ?: emptyList()

    /** Returns `true` if the token contains the specified scope. */
    fun hasScope(target: String): Boolean = scopes.any { it == target }
}

/**
 * Claims from an access token (the one sent by the client in `AuthToken`).
 */
class AccessTokenClaims(
    val issuer: String?,
    val audience: String?,
    val subject: String?,
    val username: String?,
    val issuedAt: Long?,
    val expiresAt: Long?,
    val notBefore: Long?,
    val certificateFingerprint: String?,
)

/**
 * JWT validator (Ed25519 / EdDSA).
 *
 * Mirrors the behavior of the original Hytale server:
 * - Only the `EdDSA` algorithm is accepted.
 * - Public keys are obtained from the Session Service JWKS.
 * - A clock skew of up to five minutes is allowed.
 * - The access token must be bound to the client's certificate (`cnf.x5t#S256`).
 * - If signature verification fails, the JWKS is reloaded to handle key rotation, at most
 *   once every five minutes.
 */
class JWTValidator(
    private val jwks: JwksCache,
    private val expectedIssuer: String,
    private val expectedAudience: String?,
    private val logger: Logger,
) {

    /**
     * Validates an identity token.
     * Doesn't check audience or certificate binding: only signature, issuer, and dates.
     */
    fun validateIdentityToken(token: String): IdentityTokenClaims? {
        val parsed = parse(token, "Identity token") ?: return null
        if (!verifySignature(parsed, "Identity token")) return null

        val claims = parsed.claims
        val profile = claims.getSection("profile")

        val identity = IdentityTokenClaims(
            issuer = claims.getString("iss"),
            subject = claims.getString("sub"),
            username = profile.getString("username"),
            skin = profile.getString("skin"),
            entitlements = profile.getStringList("entitlements"),
            issuedAt = (claims["iat"] as? Number)?.toLong(),
            expiresAt = (claims["exp"] as? Number)?.toLong(),
            notBefore = (claims["nbf"] as? Number)?.toLong(),
            scope = claims.getString("scope"),
        )

        if (expectedIssuer != identity.issuer) {
            logger.warn("Invalid identity token issuer: expected $expectedIssuer, got ${identity.issuer}")
            return null
        }

        if (!checkTimestamps(identity.expiresAt, identity.notBefore, identity.issuedAt, "Identity token")) {
            return null
        }

        if (identity.subject == null) {
            logger.warn("Identity token has no subject")
            return null
        }

        return identity
    }

    /**
     * Validates an access token, including the binding with the client certificate.
     */
    fun validateAccessToken(token: String, clientCert: X509Certificate?): AccessTokenClaims? {
        val parsed = parse(token, "Access token") ?: return null
        if (!verifySignature(parsed, "Access token")) return null

        val claims = parsed.claims
        val cnf = claims.getSection("cnf")
        val access = AccessTokenClaims(
            issuer = claims.getString("iss"),
            audience = audienceOf(claims["aud"]),
            subject = claims.getString("sub"),
            username = claims.getString("username"),
            issuedAt = (claims["iat"] as? Number)?.toLong(),
            expiresAt = (claims["exp"] as? Number)?.toLong(),
            notBefore = (claims["nbf"] as? Number)?.toLong(),
            certificateFingerprint = cnf.getString("x5t#S256"),
        )

        if (expectedIssuer != access.issuer) {
            logger.warn("Invalid access token issuer: expected $expectedIssuer, got ${access.issuer}")
            return null
        }
        if (expectedAudience != null && expectedAudience != access.audience) {
            logger.warn("Invalid access token audience: expected $expectedAudience, got ${access.audience}")
            return null
        }
        if (!checkTimestamps(access.expiresAt, access.notBefore, access.issuedAt, "Access token")) return null
        if (!CertificateUtil.validateCertificateBinding(access.certificateFingerprint, clientCert)) {
            logger.warn("Access token certificate binding (cnf.x5t#S256) does not match the client certificate")
            return null
        }
        if (access.subject == null) {
            logger.warn("Access token has no subject")
            return null
        }
        return access
    }

    private fun checkTimestamps(
        expiresAt: Long?,
        notBefore: Long?,
        issuedAt: Long?,
        kind: String,
    ): Boolean {
        val now = Instant.now().epochSecond
        if (expiresAt == null) {
            logger.warn("$kind has no expiration claim")
            return false
        }
        if (now >= expiresAt + CLOCK_SKEW_SECONDS) {
            logger.warn("$kind expired (exp: $expiresAt, now: $now)")
            return false
        }
        if (notBefore != null && now < notBefore - CLOCK_SKEW_SECONDS) {
            logger.warn("$kind is not valid yet (nbf: $notBefore, now: $now)")
            return false
        }
        if (issuedAt != null && issuedAt > now + CLOCK_SKEW_SECONDS) {
            logger.warn("$kind was issued in the future (iat: $issuedAt, now: $now)")
            return false
        }
        return true
    }

    /** JWT token already broken down and validated in shape */
    private class ParsedToken(
        val header: ConfigSection,
        val claims: ConfigSection,
        val signingInput: ByteArray,
        val signature: ByteArray,
        val keyId: String?,
    )

    /**
     * Parses a JWT with three parts and validates its structure.
     *
     * An Ed25519 signature is 64 bytes, or 86 characters in base64url encoding.
     */
    private fun parse(token: String, kind: String): ParsedToken? {
        if (token.isEmpty()) {
            logger.warn("$kind is empty")
            return null
        }

        val first = token.indexOf('.')
        if (first <= 0) {
            logger.warn("$kind has an invalid format (expected 3 parts)")
            return null
        }
        val second = token.indexOf('.', first + 1)
        if (second < 0 || token.indexOf('.', second + 1) >= 0) {
            logger.warn("$kind has an invalid format (expected 3 parts)")
            return null
        }

        val headerPart = token.substring(0, first)
        val claimsPart = token.substring(first + 1, second)
        val signaturePart = token.substring(second + 1)

        if (signaturePart.isEmpty()) {
            logger.warn("$kind has an empty signature")
            return null
        }

        if (signaturePart.length !in MIN_SIGNATURE_LENGTH..MAX_SIGNATURE_LENGTH) {
            logger.warn("$kind has an invalid signature length: ${signaturePart.length}")
            return null
        }

        val configService = ConfigServiceRegistry["json"]
        val base64Decoder = Base64.getUrlDecoder()

        val header = try {
            configService.load(base64Decoder.decode(headerPart))
        } catch (_: Exception) {
            logger.warn("$kind has an unreadable header")
            return null
        }

        return try {
            val claims = configService.load(base64Decoder.decode(claimsPart))
            val signature = base64Decoder.decode(signaturePart)
            ParsedToken(
                header = header,
                claims = claims,
                signingInput = token.substring(0, second).toByteArray(Charsets.US_ASCII),
                signature = signature,
                keyId = header.getString("kid"),
            )
        } catch (e: Exception) {
            logger.warn("$kind could not be parsed: ${e.javaClass.simpleName}")
            null
        }
    }

    /**
     * Verifies the token signature and refreshes the JWKS once when a key rotation may have
     * caused verification to fail.
     */
    private fun verifySignature(token: ParsedToken, kind: String): Boolean {
        val algorithm = token.header.getString("alg")
        if (algorithm != SUPPORTED_ALGORITHM) {
            logger.warn("Unsupported $kind algorithm: $algorithm (expected $SUPPORTED_ALGORITHM)")
            return false
        }

        val set = jwks.load()
        if (set == null) {
            logger.warn("Could not load the JWKS to verify the $kind")
            return false
        }
        if (verifyWith(token, set)) return true

        if (!jwks.canRefresh()) return false
        val fresh = jwks.refresh() ?: return false
        return fresh !== set && verifyWith(token, fresh)
    }

    private fun verifyWith(token: ParsedToken, set: JwkSet): Boolean {
        val key = set.find(token.keyId)
        if (key == null) {
            logger.warn("No Ed25519 key found for kid=${token.keyId}")
            return false
        }
        val publicKey = key.publicKey
        if (publicKey == null) {
            logger.warn("Could not build the Ed25519 key for kid=${token.keyId}")
            return false
        }
        return try {
            val verifier = Signature.getInstance("Ed25519")
            verifier.initVerify(publicKey)
            verifier.update(token.signingInput)
            verifier.verify(token.signature)
        } catch (_: Exception) {
            false
        }
    }

    companion object {
        /**
         * The `aud` claim may be a string or a list (RFC 7519).
         *
         * @return The first value, or `null` if absent or unsupported.
         */
        fun audienceOf(value: Any?): String? = when (value) {
            is String -> value
            is List<*> -> value.firstOrNull() as? String
            else -> null
        }

        /** The allowed clock skew, in seconds (five minutes). */
        const val CLOCK_SKEW_SECONDS: Long = 300

        const val SUPPORTED_ALGORITHM: String = "EdDSA"

        const val MIN_SIGNATURE_LENGTH: Int = 80
        const val MAX_SIGNATURE_LENGTH: Int = 90
    }
}
