package dev.sectile.network.internal.stages

import dev.sectile.network.api.player.stage.HookPhase
import dev.sectile.network.api.player.stage.StageHook
import dev.sectile.network.api.player.stage.StageHookRegistration
import dev.sectile.network.api.player.stage.StageHookRegistry
import dev.sectile.network.api.player.stage.StepDecision
import dev.sectile.network.api.player.stage.StepPoint
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Internal extension registry backing every plugin's scoped [StageHookRegistry].
 *
 * Hooks are shared globally across connections once registered, while plugins mutate this registry
 * exclusively through [ScopedStageHookProvider] so hooks are torn down automatically on unload.
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
