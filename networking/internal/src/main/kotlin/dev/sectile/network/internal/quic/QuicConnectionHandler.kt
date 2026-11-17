package dev.sectile.network.internal.quic

import dev.sectile.kernel.logger.Logger
import dev.sectile.network.internal.NetworkInternalConnector
import dev.sectile.network.internal.io.ProtocolUtil
import dev.sectile.network.internal.player.DisconnectSupport
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.handler.codec.quic.QuicChannel
import java.security.cert.Certificate
import java.security.cert.X509Certificate
import javax.net.ssl.ExtendedSSLSession
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLEngine

/**
 * Per-connection QUIC logic after the handshake. Equivalent to what QuicheConnection does on establishment:
 *  - requires a client certificate (if missing: AuthFailed close),
 *  - validates the negotiated ALPN version (if lower than the current one: the stream sends ServerDisconnect
 *    and the connection is closed with ClientOutdated),
 *  - stores the certificate and SNI, and creates the rate-limit token bucket.
 */
internal class QuicConnectionHandler(private val logger: Logger) : ChannelInboundHandlerAdapter() {

    override fun channelActive(ctx: ChannelHandlerContext) {
        super.channelActive(ctx)

        val channel = ctx.channel() as QuicChannel
        val remote = channel.remoteSocketAddress()
        logger.info("Received connection from $remote")

        val engine = channel.sslEngine()

        val certificate = engine?.let { extractClientCertificate(it) }
        if (certificate == null) {
            logger.warn("Client certificate missing from $remote, closing")
            QuicClose.application(channel, QuicApplicationErrorCode.AuthFailed)
            return
        }
        channel.attr(QuicTransport.CLIENT_CERTIFICATE_ATTR).set(certificate)

        val alpnVersion = QuicProtocol.parseAlpnVersion(engine.applicationProtocol)
        if (alpnVersion == null) {
            logger.warn("Unrecognized ALPN '${engine.applicationProtocol}' from $remote, closing")
            QuicClose.protocolViolation(channel)
            return
        }
        if (alpnVersion < QuicProtocol.PROTOCOL_VERSION) {
            // Old client: mark the connection and have the stream send ServerDisconnect
            // before closing it, so the client knows the reason.
            logger.info("Rejecting $remote: ALPN version $alpnVersion < required ${QuicProtocol.PROTOCOL_VERSION}")
            channel.attr(QuicTransport.ALPN_REJECT_ERROR_CODE_ATTR).set(QuicApplicationErrorCode.ClientOutdated)
            return
        }

        extractSni(engine)?.let { channel.attr(ProtocolUtil.SNI_HOSTNAME_KEY).set(it) }

        val config = NetworkInternalConnector.config
        if (config.rateLimitEnabled) {
            channel.attr(ProtocolUtil.RATE_LIMIT_KEY).set(
                TokenBucket(
                    config.rateLimitBurst,
                    config.rateLimitPacketsPerSecond,
                    System.nanoTime(),
                ),
            )
        }

        logger.info("Client $remote certified")
    }

    private fun extractClientCertificate(engine: SSLEngine): X509Certificate? = try {
        val peerCerts: Array<Certificate?>? = engine.session.peerCertificates
        if (!peerCerts.isNullOrEmpty() && peerCerts[0] is X509Certificate) {
            peerCerts[0] as X509Certificate
        } else {
            null
        }
    } catch (_: Exception) {
        // SSLPeerUnverifiedException: the client did not present a certificate.
        null
    }

    private fun extractSni(engine: SSLEngine): String? = try {
        (engine.session as? ExtendedSSLSession)
            ?.requestedServerNames
            ?.filterIsInstance<SNIHostName>()
            ?.firstOrNull()
            ?.asciiName
    } catch (_: Throwable) {
        null
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        logger.debug("QUIC connection error: ${cause.javaClass.simpleName}")
        QuicClose.application(ctx.channel(), QuicApplicationErrorCode.Crash)
    }
}
