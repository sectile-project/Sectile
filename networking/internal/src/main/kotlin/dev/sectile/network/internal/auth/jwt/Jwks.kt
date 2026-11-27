package dev.sectile.network.internal.auth.jwt

import dev.sectile.kernel.config.ConfigSection
import dev.sectile.kernel.config.ConfigServiceRegistry
import dev.sectile.kernel.config.section.MapConfigSection
import dev.sectile.kernel.logger.logger
import dev.sectile.network.internal.NetworkInternal
import dev.sectile.network.internal.auth.SessionServiceClient
import java.nio.file.Files
import java.nio.file.Path
import java.security.PublicKey
import java.time.Duration

/**
 * Ed25519 public key from a JWKS.
 *
 * @param kty key type (only `OKP` is accepted).
 * @param kid key identifier.
 * @param crv curve (`Ed25519`).
 * @param x base64url u coordinate (32 bytes, little-endian).
 */
class JwkKey(
    val kty: String,
    val kid: String?,
    val crv: String?,
    val x: String?,
) {
    /** `true` if the key is usable for verifying Ed25519 signatures. */
    val usable: Boolean get() = kty == "OKP" && crv == "Ed25519" && !x.isNullOrEmpty()

    /**
     * Pre-built and cached Ed25519 public key to avoid decoding base64url and
     * constructing `EdECPublicKeySpec` on every player login.
     */
    val publicKey: PublicKey? by lazy {
        if (!usable) null else Ed25519Keys.fromJwkX(x!!)
    }
}

/**
 * Set of public keys (JWKS).
 */
class JwkSet(val keys: List<JwkKey>) {

    /**
     * Finds the key that corresponds to the given `kid`.
     *
     * If the token does not carry a `kid`, the first OKP key in the set is used (same as in the
     * original server).
     */
    fun find(keyId: String?): JwkKey? {
        for (key in keys) {
            if (key.usable && (keyId == null || keyId == key.kid)) return key
        }
        return null
    }

    companion object {
        fun parse(json: ConfigSection): JwkSet? {
            val array = json["keys"] as? Collection<*> ?: return null

            val keys = array.mapNotNull { element ->
                val section = element as? ConfigSection ?: return@mapNotNull null
                val kty = section.getString("kty") ?: return@mapNotNull null

                JwkKey(
                    kty = kty,
                    kid = section.getString("kid"),
                    crv = section.getString("crv"),
                    x = section.getString("x"),
                )
            }
            return JwkSet(keys)
        }
    }
}

/**
 * JWKS cache.
 *
 * The set is kept in memory and persisted in `.jwks.json` (next to the process) so that it
 * does not have to be requested over the network at startup. When a signature fails to
 * validate, a reload can be forced (key rotation), with a minimum interval between reloads.
 */
class JwksCache(private val sessionService: SessionServiceClient) {

    @Volatile
    private var cached: JwkSet? = null

    @Volatile
    private var lastRefresh: Long = 0L

    private val lock = Any()

    companion object {
        const val CACHE_FILE: String = ".jwks.json"
        val MIN_REFRESH_INTERVAL_NANOS: Long = Duration.ofMinutes(5).toNanos()
    }

    fun load(): JwkSet? {
        cached?.let { return it }

        synchronized(lock) {
            cached?.let { return it }
            loadFromDisk()?.let {
                cached = it
                return it
            }
            return fetch()
        }
    }

    fun refresh(): JwkSet? {
        synchronized(lock) {
            return fetch()
        }
    }

    fun canRefresh(): Boolean {
        val last = lastRefresh
        return last == 0L || System.nanoTime() - last >= MIN_REFRESH_INTERVAL_NANOS
    }

    private fun fetch(): JwkSet? {
        val set = sessionService.fetchJwks()
        if (set == null || set.keys.isEmpty()) {
            return cached
        }
        cached = set
        lastRefresh = System.nanoTime()
        saveToDisk(set)
        return set
    }

    private fun loadFromDisk(): JwkSet? {
        val file = Path.of(CACHE_FILE)
        if (!Files.exists(file)) {
            return null
        }

        return try {
            JwkSet.parse(NetworkInternal.json.load(file))
        } catch (e: Exception) {
            logger().error("Failed to load jwks.json", e)
            null
        }
    }

    private fun saveToDisk(set: JwkSet) {
        try {
            val keys = set.keys.map { key ->
                linkedMapOf<String, Any?>().apply {
                    put("kty", key.kty)
                    key.kid?.let { put("kid", it) }
                    key.crv?.let { put("crv", it) }
                    key.x?.let { put("x", it) }
                }
            }
            val section = MapConfigSection(linkedMapOf("keys" to keys))
            NetworkInternal.json.save(Path.of(CACHE_FILE), section)
        } catch (e: Exception) {
            logger().error("Failed to save jwks.json", e)
        }
    }
}
