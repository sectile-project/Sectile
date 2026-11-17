package dev.sectile.network.internal.auth

import dev.sectile.kernel.config.ConfigServiceRegistry
import dev.sectile.kernel.logger.logger
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpRequest
import java.nio.charset.StandardCharsets
import java.time.Duration

/**
 * Response from the device authorization endpoint.
 *
 * @param deviceCode opaque code used to request the token.
 * @param userCode short code that the user types.
 * @param verificationUri page where the code is entered.
 * @param verificationUriComplete page with the code already entered.
 * @param expiresIn seconds of validity.
 * @param interval seconds between polling attempts.
 */
data class DeviceAuthorization(
    val deviceCode: String,
    val userCode: String,
    val verificationUri: String,
    val verificationUriComplete: String?,
    val expiresIn: Int,
    val interval: Int,
)

/**
 * OAuth2 token endpoint response.
 *
 * @param error error code (`authorization_pending`, `slow_down`, ...).
 */
data class OAuthTokens(
    val accessToken: String?,
    val refreshToken: String?,
    val idToken: String?,
    val error: String?,
    val expiresIn: Int,
)

/**
 * OAuth2 client for the Hytale server.
 *
 * Supports the device authorization flow (the most convenient for remote servers:
 * the user opens a page in the browser and types a code) and token refreshing.
 */
class OAuthClient(
    private val timeout: Duration = Duration.ofSeconds(30),
    private val userAgent: String = USER_AGENT,
) {

    private val tokenUri: URI = URI.create(OAUTH_TOKEN_URL)
    private val deviceAuthUri: URI = URI.create(DEVICE_AUTH_URL)
    private val encodedClientId: String = encode(CLIENT_ID)
    private val encodedScopes: String = encode(SCOPES.joinToString(" "))

    companion object {
        const val DOMAIN: String = "hytale.com"

        const val OAUTH_AUTH_URL: String = "https://oauth.accounts.$DOMAIN/oauth2/auth"
        const val OAUTH_TOKEN_URL: String = "https://oauth.accounts.$DOMAIN/oauth2/token"
        const val DEVICE_AUTH_URL: String = "https://oauth.accounts.$DOMAIN/oauth2/device/auth"
        const val SESSION_SERVICE_URL: String = "https://sessions.$DOMAIN"
        const val ACCOUNT_DATA_URL: String = "https://account-data.$DOMAIN"

        const val CLIENT_ID: String = "hytale-server"

        val SCOPES: List<String> = listOf("openid", "offline", "auth:server")

        const val DEVICE_GRANT_TYPE: String = "urn:ietf:params:oauth:grant-type:device_code"
        const val USER_AGENT: String = "Sectile"

        private val ENCODED_DEVICE_GRANT_TYPE: String = URLEncoder.encode(DEVICE_GRANT_TYPE, StandardCharsets.UTF_8)
    }

    fun requestDeviceAuthorization(): DeviceAuthorization? {
        val body = "client_id=$encodedClientId&scope=$encodedScopes"

        val response = post(deviceAuthUri, body, acceptBadRequest = false) ?: return null
        return try {
            val json = ConfigServiceRegistry["json"].load(response)
            val deviceCode = json.getString("device_code") ?: return null
            DeviceAuthorization(
                deviceCode = deviceCode,
                userCode = json.getString("user_code") ?: "",
                verificationUri = json.getString("verification_uri") ?: "",
                verificationUriComplete = json.getString("verification_uri_complete"),
                expiresIn = json.getInt("expires_in", 600).coerceIn(1, 3600),
                interval = json.getInt("interval", 5).coerceIn(1, 60),
            )
        } catch (e: Exception) {
            logger().warn("Unreadable device authorization response: ${e.javaClass.simpleName}")
            null
        }
    }

    fun pollDeviceToken(deviceCode: String): OAuthTokens? = postTokens(
        "grant_type=$ENCODED_DEVICE_GRANT_TYPE&client_id=$encodedClientId&device_code=" + encode(deviceCode),
    )

    fun refreshTokens(refreshToken: String): OAuthTokens? = postTokens(
        "grant_type=refresh_token&client_id=$encodedClientId&refresh_token=" + encode(refreshToken),
    )

    private fun postTokens(body: String): OAuthTokens? {
        val response = post(tokenUri, body, acceptBadRequest = true) ?: return null
        return try {
            val json = ConfigServiceRegistry["json"].load(response)
            OAuthTokens(
                accessToken = json.getString("access_token"),
                refreshToken = json.getString("refresh_token"),
                idToken = json.getString("id_token"),
                error = json.getString("error"),
                expiresIn = json.getInt("expires_in", 600),
            )
        } catch (e: Exception) {
            logger().warn("Unreadable OAuth token response: ${e.javaClass.simpleName}")
            null
        }
    }

    private fun post(uri: URI, body: String, acceptBadRequest: Boolean): ByteArray? {
        val request = HttpRequest.newBuilder(uri)
            .header("Content-Type", "application/x-www-form-urlencoded")
            .header("Accept", "application/json")
            .header("User-Agent", userAgent)
            .timeout(timeout)
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()

        return try {
            val response = AuthHttp.send(request)
            if (response.status == 200 || (acceptBadRequest && response.status == 400)) response.body else null
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            null
        } catch (e: Exception) {
            logger().error("Error during OAuth request to $uri", e)
            null
        }
    }

    private fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8)
}
