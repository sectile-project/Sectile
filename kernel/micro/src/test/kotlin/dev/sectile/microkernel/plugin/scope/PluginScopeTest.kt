package dev.sectile.microkernel.plugin.scope

import dev.sectile.kernel.plugin.model.PluginId
import dev.sectile.kernel.plugin.scope.provider.CloseableDependencyProvider
import dev.sectile.kernel.plugin.scope.provider.DependencyProvider
import dev.sectile.microkernel.plugin.classloading.PluginClassLoader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class PluginScopeTest {

    @Test
    fun `closing kernel plugin scope is idempotent and rejects subsequent operations`() {
        val scope = KernelPluginScope(PluginId("test-plugin"))
        val events = mutableListOf<String>()

        scope.put(object : CloseableDependencyProvider<String> {
            override val type: Class<String> = String::class.java
            override fun provide(): String = "value"
            override fun close() {
                events.add("provider-closed")
            }
        })
        scope.onClose { events.add("hook-1") }
        scope.onClose { events.add("hook-2") }

        assertEquals("value", scope[String::class.java])

        scope.close()
        scope.close()

        assertEquals(listOf("hook-2", "hook-1", "provider-closed"), events)

        assertFailsWith<IllegalStateException> { scope[String::class.java] }
        assertFailsWith<IllegalStateException> { scope.getProvider(String::class.java) }
        assertFailsWith<IllegalStateException> { scope.remove(String::class.java) }
        assertFailsWith<IllegalStateException> {
            scope.put(object : DependencyProvider<Int> {
                override val type: Class<Int> = Int::class.java
                override fun provide(): Int = 1
            })
        }

        var lateHookRan = false
        assertFailsWith<IllegalStateException> {
            scope.onClose { lateHookRan = true }
        }
        assertTrue(lateHookRan)
    }

    @Test
    fun `closed plugin classloader cannot resurrect its scope`() {
        val loader = PluginClassLoader(
            id = PluginId("test-resurrection"),
            urls = emptyArray(),
            dependencyClassLoaders = emptyList(),
            parentClassLoader = this::class.java.classLoader,
        )

        assertFalse(loader.isClosed)
        val scope = KernelPluginScopeFactory.acquire(loader)
        var hookClosed = false
        scope.onClose { hookClosed = true }

        assertSame(scope, KernelPluginScopeFactory.acquire(loader))

        loader.close()
        assertTrue(loader.isClosed)
        assertTrue(hookClosed)

        loader.close()

        assertFailsWith<IllegalStateException> {
            KernelPluginScopeFactory.acquire(loader)
        }
        assertNull(PluginsScopeContainer.removeScope(loader))
    }

    @Test
    fun `closing onClose handle removes hook before scope closes`() {
        val scope = KernelPluginScope(PluginId("test-handle"))
        var removedHookRan = false
        var activeHookRan = false

        val handle = scope.onClose { removedHookRan = true }
        scope.onClose { activeHookRan = true }

        handle.close()
        handle.close()

        scope.close()

        assertFalse(removedHookRan)
        assertTrue(activeHookRan)
    }
}
