package dev.sectile.network.internal.auth

import java.io.IOException
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * HTTP client shared by the entire authentication module.
 *
 * - A single [HttpClient] (connection pool, threads, and TLS context) for OAuth, Session
 *   Service, and JWKS, instead of one per client/refresh.
 * - Does not follow redirects: `HttpClient` forwards the headers (including `Authorization`)
 *   to the redirect destination, which could leak the Bearer tokens.
 * - Limits the size of the responses so that a malicious service or a MITM cannot
 *   exhaust the server's memory.
 */
internal object AuthHttp {

    /** Maximum size of a response (1 MiB): the service's responses take up a few KB. */
    const val MAX_RESPONSE_BYTES: Int = 1 shl 20

    val client: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(15))
        .followRedirects(HttpClient.Redirect.NEVER)
        .build()

    class Response(val status: Int, val body: ByteArray)

    /**
     * Sends the request and reads the body on the calling thread (never on the client's
     * executor), aborting if it exceeds [MAX_RESPONSE_BYTES].
     */
    @Throws(IOException::class, InterruptedException::class)
    fun send(request: HttpRequest): Response {
        val response = client.send(request, HttpResponse.BodyHandlers.ofInputStream())
        val declared = response.headers().firstValueAsLong("Content-Length").orElse(-1L)
        response.body().use { stream ->
            if (declared > MAX_RESPONSE_BYTES) {
                throw IOException("Response too large: $declared bytes")
            }
            val body = stream.readNBytes(MAX_RESPONSE_BYTES + 1)
            if (body.size > MAX_RESPONSE_BYTES) {
                throw IOException("Response too large (> $MAX_RESPONSE_BYTES bytes)")
            }
            return Response(response.statusCode(), body)
        }
    }
}
