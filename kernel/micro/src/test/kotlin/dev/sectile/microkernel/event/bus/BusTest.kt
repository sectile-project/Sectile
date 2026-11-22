package dev.sectile.microkernel.event.bus

import dev.sectile.kernel.event.Cancellable
import dev.sectile.kernel.event.EventBus
import dev.sectile.kernel.event.Listener
import dev.sectile.kernel.event.handler.ListenerPhase
import dev.sectile.kernel.event.subscribe
import dev.sectile.microkernel.logger.PrintlnLogger
import dev.sectile.microkernel.plugin.scope.KernelPluginScope
import dev.sectile.microkernel.plugin.scope.provider.ScopedEventBusProvider
import org.junit.jupiter.api.Assertions.assertTrue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class BusTest {
    @Test
    fun `should receive event when subscribed using lambda`() {
        val eventBus = DefaultEventBus(PrintlnLogger())
        var receivedUsername = ""

        eventBus.subscribe<String> { name -> receivedUsername = name }

        eventBus.publish("ManoloGaymer")

        assertEquals("ManoloGaymer", receivedUsername, "The listener should receive the exact event posted")
    }

    @Test
    fun `should respect listener priority and phases`() {
        val eventBus = DefaultEventBus(PrintlnLogger())
        val executionOrder = mutableListOf<String>()

        eventBus.subscribe<ChatEvent>(ListenerPhase.MONITOR) { executionOrder.add("MONITOR") }
        eventBus.subscribe<ChatEvent>(ListenerPhase.FINAL) { executionOrder.add("FINAL") }
        eventBus.subscribe<ChatEvent>(ListenerPhase.LATE) { executionOrder.add("LATE") }
        eventBus.subscribe<ChatEvent>(ListenerPhase.DEFAULT, priority = 100) { executionOrder.add("DEFAULT_HIGH_PRIORITY") }
        eventBus.subscribe<ChatEvent>(ListenerPhase.DEFAULT, priority = -10) { executionOrder.add("DEFAULT_LOW_PRIORITY") }
        eventBus.subscribe<ChatEvent>(ListenerPhase.EARLY) { executionOrder.add("EARLY") }
        eventBus.subscribe<ChatEvent>(ListenerPhase.INITIAL) { executionOrder.add("INITIAL") }

        eventBus.publish(ChatEvent("Hello Server!"))

        val expectedOrder = listOf("INITIAL", "EARLY", "DEFAULT_HIGH_PRIORITY", "DEFAULT_LOW_PRIORITY", "LATE", "FINAL", "MONITOR")
        assertEquals(expectedOrder, executionOrder, "Events must be executed following strict Phase and Priority order")
    }

    @Test
    fun `should stop receiving events after unregistering subscription`() {
        val eventBus = DefaultEventBus(PrintlnLogger())
        var eventCount = 0

        assertEquals(0, eventBus.size())
        val subscription = eventBus.subscribe<UserJoinEvent> { eventCount++ }
        assertEquals(1, eventBus.size())

        eventBus.publish(UserJoinEvent("Player1"))
        subscription.unsubscribe()
        assertEquals(0, eventBus.size())
        eventBus.publish(UserJoinEvent("Player2"))

        assertEquals(1, eventCount, "Listener must not be triggered after unregister is called")
    }

    @Test
    fun `should process annotated listeners correctly`() {
        val eventBus = DefaultEventBus(PrintlnLogger())
        val exampleJoinListener = ExampleJoinListener()

        eventBus.subscribe(exampleJoinListener)

        eventBus.publish(UserJoinEvent("Spammer"))

        assertTrue(exampleJoinListener.wasChecked, "Annotated methods must be registered and executed")
    }

    @Test
    fun `should skip normal listeners if event is cancelled`() {
        val eventBus = DefaultEventBus(PrintlnLogger())
        var normalListenerRan = false
        var ignoreCancelledListenerRan = false

        eventBus.subscribe<DamageEvent>(ListenerPhase.INITIAL) { event ->
            event.cancelled = true
        }
        eventBus.subscribe<DamageEvent>(ListenerPhase.DEFAULT) {
            normalListenerRan = true
        }
        eventBus.subscribe<DamageEvent>(ListenerPhase.MONITOR, ignoreCancelled = true) {
            ignoreCancelledListenerRan = true
        }

        eventBus.publish(DamageEvent(10.0))
        assertFalse(normalListenerRan, "Normal listeners should skip cancelled events")
        assertTrue(ignoreCancelledListenerRan, "Listeners with ignoreCancelled=true must run")
    }

    @Test
    fun `should keep remaining phase listeners when one phase unsubscribes`() {
        val eventBus = DefaultEventBus(PrintlnLogger())
        var defaultCount = 0

        val early = eventBus.subscribe<UserJoinEvent>(ListenerPhase.EARLY) { }
        val default = eventBus.subscribe<UserJoinEvent>(ListenerPhase.DEFAULT) { defaultCount++ }

        assertEquals(1, eventBus.size())

        early.unsubscribe()
        assertEquals(1, eventBus.size())

        eventBus.publish(UserJoinEvent("Alice"))
        assertEquals(1, defaultCount)

        default.unsubscribe()
        assertEquals(0, eventBus.size())
    }

    @Test
    fun `closing one plugin scope only unsubscribes its own listeners across phases`() {
        val eventBus = DefaultEventBus(PrintlnLogger())
        val received = mutableListOf<String>()

        eventBus.subscribe<ChatEvent>(ListenerPhase.EARLY) { received.add("root:${it.message}") }

        val scopeA = KernelPluginScope().apply {
            put(ScopedEventBusProvider.fromLazy(eventBus))
        }
        val scopeB = KernelPluginScope().apply {
            put(ScopedEventBusProvider.fromLazy(eventBus))
        }

        val busA = requireNotNull(scopeA[EventBus::class.java])
        val busB = requireNotNull(scopeB[EventBus::class.java])

        busA.subscribe<ChatEvent>(ListenerPhase.DEFAULT) { received.add("a-default:${it.message}") }
        busA.subscribe<ChatEvent>(ListenerPhase.LATE) { received.add("a-late:${it.message}") }
        busB.subscribe<ChatEvent>(ListenerPhase.DEFAULT) { received.add("b-default:${it.message}") }

        eventBus.publish(ChatEvent("1"))
        assertEquals(listOf("root:1", "a-default:1", "b-default:1", "a-late:1"), received)

        received.clear()
        scopeA.close()

        eventBus.publish(ChatEvent("2"))
        assertEquals(listOf("root:2", "b-default:2"), received)

        scopeB.close()
        assertEquals(1, eventBus.size())
    }

    // Example events
    data class UserJoinEvent(
        val username: String,
    )

    data class ChatEvent(
        val message: String,
    )

    data class DamageEvent(
        val amount: Double,
        override var cancelled: Boolean = false,
    ) : Cancellable

    class ExampleJoinListener {
        var wasChecked = false

        @Listener(ListenerPhase.EARLY)
        fun onUserJoin(event: UserJoinEvent) {
            wasChecked = true
        }
    }
}
