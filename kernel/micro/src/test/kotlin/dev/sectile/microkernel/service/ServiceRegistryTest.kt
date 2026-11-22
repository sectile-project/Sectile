package dev.sectile.microkernel.service

import dev.sectile.kernel.plugin.model.PluginId
import dev.sectile.kernel.plugin.scope.provider.ProtectedLazyProvider
import dev.sectile.kernel.service.ServiceRegistry
import dev.sectile.kernel.service.all
import dev.sectile.kernel.service.contains
import dev.sectile.kernel.service.get
import dev.sectile.kernel.service.register
import dev.sectile.kernel.service.unregister
import dev.sectile.microkernel.plugin.scope.KernelPluginScope
import dev.sectile.microkernel.plugin.scope.provider.ScopedServiceRegistryProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ServiceRegistryTest {

    private fun interface GreetingService {
        fun greet(): String
    }

    @Test
    fun `resolves highest priority provider and falls back when scope closes`() {
        val registry = DefaultServiceRegistry()
        val lowScope = KernelPluginScope(PluginId("low")).apply {
            put(ProtectedLazyProvider(ServiceRegistry::class.java) { ScopedServiceRegistryProvider(registry) })
        }
        val highScope = KernelPluginScope(PluginId("high")).apply {
            put(ProtectedLazyProvider(ServiceRegistry::class.java) { ScopedServiceRegistryProvider(registry) })
        }

        val lowRegistry = requireNotNull(lowScope[ServiceRegistry::class.java])
        val highRegistry = requireNotNull(highScope[ServiceRegistry::class.java])

        lowRegistry.register<GreetingService>(GreetingService { "low" }, priority = 10)
        highRegistry.register<GreetingService>(GreetingService { "high" }, priority = 100)

        assertTrue(registry.contains<GreetingService>())
        assertEquals("high", registry.get<GreetingService>()?.greet())
        assertEquals(listOf("high", "low"), registry.all<GreetingService>().map { it.greet() })

        highScope.close()

        assertEquals("low", registry.get<GreetingService>()?.greet())
        assertEquals(listOf("low"), registry.all<GreetingService>().map { it.greet() })

        lowScope.close()

        assertFalse(registry.contains<GreetingService>())
        assertNull(registry.get<GreetingService>())
        assertTrue(registry.all<GreetingService>().isEmpty())
    }

    @Test
    fun `scopes cannot unregister services registered by root or other scopes`() {
        val registry = DefaultServiceRegistry()
        val rootImpl = GreetingService { "root" }
        val implA = GreetingService { "a" }
        val implB = GreetingService { "b" }

        registry.register<GreetingService>(rootImpl, priority = 0)

        val scopedA = ScopedServiceRegistryProvider(registry)
        val scopedB = ScopedServiceRegistryProvider(registry)

        scopedA.register<GreetingService>(implA, priority = 10)
        scopedB.register<GreetingService>(implB, priority = 20)

        assertFalse(scopedA.unregister<GreetingService>(rootImpl))
        assertFalse(scopedA.unregister<GreetingService>(implB))
        assertEquals("b", registry.get<GreetingService>()?.greet())

        assertTrue(scopedA.unregister<GreetingService>(implA))
        assertEquals(listOf("b", "root"), registry.all<GreetingService>().map { it.greet() })

        scopedB.close()
        assertEquals("root", registry.get<GreetingService>()?.greet())
    }

    @Test
    fun `direct registration unregister removes service from scoped provider`() {
        val registry = DefaultServiceRegistry()
        val scoped = ScopedServiceRegistryProvider(registry)
        val impl = GreetingService { "direct" }

        val registration = scoped.register<GreetingService>(impl)
        assertTrue(registry.contains<GreetingService>())

        registration.unregister()
        registration.unregister()

        assertFalse(registry.contains<GreetingService>())
        assertFalse(scoped.unregister<GreetingService>(impl))
    }
}
