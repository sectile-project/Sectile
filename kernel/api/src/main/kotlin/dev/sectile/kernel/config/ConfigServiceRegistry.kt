package dev.sectile.kernel.config

import dev.sectile.kernel.Kernel
import java.util.concurrent.ConcurrentHashMap

object ConfigServiceRegistry {

    private val services = ConcurrentHashMap<String, ConfigService>()
    private val scopeHandles = ConcurrentHashMap<ConfigService, AutoCloseable>()

    fun register(service: ConfigService) {
        require(service.fileExtensions.isNotEmpty()) {
            "ConfigService must declare at least one file extension."
        }

        service.fileExtensions.forEach { extension ->
            services[normalizeExtension(extension)] = service
        }

        if (Kernel.isInitialized()) {
            runCatching { Kernel.pluginScopeFactory.acquire(service) }
                .getOrNull()
                ?.onClose { unregister(service) }
                ?.let { handle -> scopeHandles.put(service, handle)?.close() }
        }
    }

    fun unregister(service: ConfigService) {
        scopeHandles.remove(service)?.close()
        service.fileExtensions.forEach { extension ->
            services.remove(normalizeExtension(extension), service)
        }
    }

    operator fun get(extension: String): ConfigService = services[normalizeExtension(extension)]
        ?: throw IllegalArgumentException("No config service registered for extension: $extension")

    private fun normalizeExtension(extension: String): String = extension.trim().removePrefix(".").lowercase()
}
