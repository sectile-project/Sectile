package dev.sectile.network.internal.quic.certificate

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.security.KeyPair
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

object SelfSignedCertGenerator {

    // OID 1.2.840.10045.4.3.2 = ecdsa-with-SHA256
    private val SIG_ALG_ID = seq(
        byteArrayOf(0x06, 0x08, 0x2A, 0x86.toByte(), 0x48, 0xCE.toByte(), 0x3D, 0x04, 0x03, 0x02),
    )

    // OID 2.5.4.3 = commonName
    private val OID_CN = byteArrayOf(0x06, 0x03, 0x55, 0x04, 0x03)

    private val UTC_FMT = DateTimeFormatter.ofPattern("yyMMddHHmmss'Z'").withZone(ZoneOffset.UTC)

    @Throws(Exception::class)
    fun generate(keyPair: KeyPair): X509Certificate {
        val now = System.currentTimeMillis()

        val name = seq(set(seq(OID_CN, tag(0x0C, "Hytale Server".toByteArray()))))
        val serial = tag(0x02, BigInteger.valueOf(now).toByteArray())
        val validity = seq(
            utcTime(Instant.ofEpochMilli(now - 1000L)),
            utcTime(Instant.ofEpochMilli(now).plusSeconds(365L * 24 * 3600)),
        )

        val tbs = seq(
            tag(0xA0, tag(0x02, byteArrayOf(2))), // version v3
            serial,
            SIG_ALG_ID,
            name, // issuer
            validity,
            name, // subject
            keyPair.public.encoded, // SubjectPublicKeyInfo (ya viene en DER)
        )

        val sig = Signature.getInstance("SHA256withECDSA").run {
            initSign(keyPair.private)
            update(tbs)
            sign()
        }

        val cert = seq(
            tbs,
            SIG_ALG_ID,
            tag(0x03, byteArrayOf(0) + sig), // BIT STRING, 0 unused bits
        )

        return CertificateFactory.getInstance("X.509")
            .generateCertificate(ByteArrayInputStream(cert)) as X509Certificate
    }

    // --- Mini encoder DER ---

    private fun tag(t: Int, content: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(t)
        val len = content.size
        when {
            len < 0x80 -> out.write(len)

            len < 0x100 -> {
                out.write(0x81)
                out.write(len)
            }

            else -> {
                out.write(0x82)
                out.write(len shr 8)
                out.write(len and 0xFF)
            }
        }
        out.write(content)
        return out.toByteArray()
    }

    private fun seq(vararg parts: ByteArray) = tag(0x30, concat(parts))
    private fun set(vararg parts: ByteArray) = tag(0x31, concat(parts))
    private fun utcTime(i: Instant) = tag(0x17, UTC_FMT.format(i).toByteArray())

    private fun concat(parts: Array<out ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        parts.forEach { out.write(it) }
        return out.toByteArray()
    }
}
