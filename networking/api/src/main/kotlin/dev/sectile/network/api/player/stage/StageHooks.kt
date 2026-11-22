package dev.sectile.network.api.player.stage

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Registry of the extensions of the stage machine.
 *
 * A plugin registers a [StageHook] on the steps it cares about and decides, on every one of
 * them, whether the connection continues, is closed or moves to another stage. Everything
 * happens on the public API: no transport type is involved.
 *
 * ```
 * StageHooks.after(StepPoint(ConnectionStage.AUTHENTICATION, StageSteps.AUTHENTICATION_IDENTITY_TOKEN)) { ctx ->
 *     if (isBot(ctx.connection.getIp())) {
 *         StepDecision.Reject("client.general.disconnect.banned")
 *     } else {
 *         StepDecision.Continue
 *     }
 * }
 * ```
 *
 * The registry is global: the hooks are shared by every connection of the server.
 *
 * When several hooks are registered on the same point they run in registration order and the
 * first one that does not return [StepDecision.Continue] wins: the hooks after it do not run.
 */
interface StageHookRegistration {

    fun unregister()
}

interface StageHookRegistry {

    fun before(point: StepPoint, hook: StageHook): StageHookRegistration

    fun after(point: StepPoint, hook: StageHook): StageHookRegistration

    fun beforeEveryStep(hook: StageHook): StageHookRegistration

    fun afterEveryStep(hook: StageHook): StageHookRegistration
}

/**
 * Extension point for plugins to intercept login and connection lifecycle steps.
 */
object StageHooks : StageHookRegistry {

    /** Hooks that run before a step, indexed by point. */
    private val beforeHooks = ConcurrentHashMap<StepPoint, CopyOnWriteArrayList<StageHook>>()

    /** Hooks that run after a step, indexed by point. */
    private val afterHooks = ConcurrentHashMap<StepPoint, CopyOnWriteArrayList<StageHook>>()

    /** Hooks that run before every step of every stage. */
    private val beforeEveryStep = CopyOnWriteArrayList<StageHook>()

    /** Hooks that run after every step of every stage. */
    private val afterEveryStep = CopyOnWriteArrayList<StageHook>()

    override fun before(point: StepPoint, hook: StageHook): StageHookRegistration {
        val hooks = beforeHooks.computeIfAbsent(point) { CopyOnWriteArrayList() }
        hooks.add(hook)
        return registration { hooks.remove(hook) }
    }

    override fun after(point: StepPoint, hook: StageHook): StageHookRegistration {
        val hooks = afterHooks.computeIfAbsent(point) { CopyOnWriteArrayList() }
        hooks.add(hook)
        return registration { hooks.remove(hook) }
    }

    override fun beforeEveryStep(hook: StageHook): StageHookRegistration {
        beforeEveryStep.add(hook)
        return registration { beforeEveryStep.remove(hook) }
    }

    override fun afterEveryStep(hook: StageHook): StageHookRegistration {
        afterEveryStep.add(hook)
        return registration { afterEveryStep.remove(hook) }
    }

    private fun registration(onUnregister: () -> Unit): StageHookRegistration = object : StageHookRegistration {
        @Volatile
        private var active = true

        override fun unregister() {
            val shouldRemove = synchronized(this) {
                if (!active) {
                    false
                } else {
                    active = false
                    true
                }
            }
            if (shouldRemove) {
                onUnregister()
            }
        }
    }

    /**
     * Removes every registered hook (useful for tests).
     */
    fun clear() {
        beforeHooks.clear()
        afterHooks.clear()
        beforeEveryStep.clear()
        afterEveryStep.clear()
    }

    /**
     * Returns the hooks registered for [point] in the given [phase].
     */
    fun hooksAt(point: StepPoint, phase: HookPhase): List<StageHook> = when (phase) {
        HookPhase.BEFORE -> beforeHooks[point]
        HookPhase.AFTER -> afterHooks[point]
    }.orEmpty()

    /**
     * Returns the hooks registered for every step in the given [phase].
     */
    fun globalHooks(phase: HookPhase): List<StageHook> = when (phase) {
        HookPhase.BEFORE -> beforeEveryStep
        HookPhase.AFTER -> afterEveryStep
    }
}
