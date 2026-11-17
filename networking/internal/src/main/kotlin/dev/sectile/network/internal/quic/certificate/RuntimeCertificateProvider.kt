package dev.sectile.network.internal.quic.certificate

import java.nio.file.Path
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64
import kotlin.io.path.*

data class CertificateBundle(
    val certificate: X509Certificate,
    val privateKey: PrivateKey,
)

object RuntimeCertificateProvider {

    private val PEM_LINE = Base64.getMimeEncoder(64, "\n".toByteArray())

    fun loadOrCreate(certPath: Path, keyPath: Path): CertificateBundle {
        if (certPath.exists() && keyPath.exists()) {
            val loaded = runCatching { load(certPath, keyPath) }.getOrNull()
            if (loaded != null && isStillValid(loaded.certificate)) return loaded
        }

        certPath.parent?.createDirectories()
        keyPath.parent?.createDirectories()

        val bundle = generate()
        save(bundle, certPath, keyPath)
        return bundle
    }

    private fun isStillValid(cert: X509Certificate): Boolean = try {
        cert.checkValidity()
        true
    } catch (_: CertificateException) {
        false
    }

    private fun load(certPath: Path, keyPath: Path): CertificateBundle {
        val cert = certPath.inputStream().use {
            CertificateFactory.getInstance("X.509").generateCertificate(it) as X509Certificate
        }

        val base64 = keyPath.readText()
            .lineSequence()
            .filterNot { it.startsWith("-----") }
            .joinToString("")
        val der = Base64.getMimeDecoder().decode(base64)

        // The algorithm comes from the certificate itself ("EC"), no need for BouncyCastle.
        val key = KeyFactory.getInstance(cert.publicKey.algorithm)
            .generatePrivate(PKCS8EncodedKeySpec(der))

        return CertificateBundle(cert, key)
    }

    private fun save(bundle: CertificateBundle, certPath: Path, keyPath: Path) {
        certPath.writeText(toPem("CERTIFICATE", bundle.certificate.encoded))
        keyPath.writeText(toPem("PRIVATE KEY", bundle.privateKey.encoded)) // PKCS#8
    }

    private fun toPem(type: String, der: ByteArray): String = "-----BEGIN $type-----\n${PEM_LINE.encodeToString(der)}\n-----END $type-----\n"

    private fun generate(): CertificateBundle {
        // The official server uses Netty's SelfSignedCertificate with CN=localhost; here the same
        // kind of certificate is generated (EC, CN=localhost) without depending on BouncyCastle,
        // which is not available by default on modern JDKs.
        val keyGen = KeyPairGenerator.getInstance("EC")
        keyGen.initialize(ECGenParameterSpec("secp256r1"))
        val keyPair = keyGen.generateKeyPair()
        return CertificateBundle(SelfSignedCertGenerator.generate(keyPair), keyPair.private)
    }
}
