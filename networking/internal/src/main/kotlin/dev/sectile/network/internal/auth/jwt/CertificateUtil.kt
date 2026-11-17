package dev.sectile.network.internal.auth.jwt

import java.security.MessageDigest
import java.security.cert.X509Certificate
import java.util.Base64

/**
 * Utilities for the client's X.509 certificate (mTLS).
 */
object CertificateUtil {

    /**
     * SHA-256 fingerprint of the certificate in base64url without padding (RFC 7515, `x5t#S256`).
     *
     * This is the value that travels in the `cnf.x5t#S256` claim of the access token and the one the
     * server sends to the Session Service as `x509Fingerprint`.
     */
    fun computeCertificateFingerprint(certificate: X509Certificate): String? = try {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(certificate.encoded)
        Base64.getUrlEncoder().withoutPadding().encodeToString(hash)
    } catch (_: Exception) {
        null
    }

    /**
     * Checks the certificate binding (RFC 8705) between the token's claim and the
     * actual certificate of the mTLS connection.
     */
    fun validateCertificateBinding(jwtFingerprint: String?, clientCert: X509Certificate?): Boolean {
        if (clientCert == null || jwtFingerprint.isNullOrEmpty()) {
            return false
        }
        val actual = computeCertificateFingerprint(clientCert) ?: return false
        return MessageDigest.isEqual(
            jwtFingerprint.toByteArray(Charsets.UTF_8),
            actual.toByteArray(Charsets.UTF_8),
        )
    }
}
