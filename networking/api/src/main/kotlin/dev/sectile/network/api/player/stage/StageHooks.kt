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
object StageHooks {

    /** Hooks that run before a step, indexed by point. */
    private val beforeHooks = ConcurrentHashMap<StepPoint, CopyOnWriteArrayList<StageHook>>()

    /** Hooks that run after a step, indexed by point. */
    private val afterHooks = ConcurrentHashMap<StepPoint, CopyOnWriteArrayList<StageHook>>()

    /** Hooks that run before every step of every stage. */
    private val beforeEveryStep = CopyOnWriteArrayList<StageHook>()

    /** Hooks that run after every step of every stage. */
    private val afterEveryStep = CopyOnWriteArrayList<StageHook>()

    /**
     * Registers a hook that runs before the step of [point].
     */
    fun before(point: StepPoint, hook: StageHook) {
        beforeHooks.computeIfAbsent(point) { CopyOnWriteArrayList() }.add(hook)
    }

    /**
     * Registers a hook that runs after the step of [point] finished.
     */
    fun after(point: StepPoint, hook: StageHook) {
        afterHooks.computeIfAbsent(point) { CopyOnWriteArrayList() }.add(hook)
    }

    /**
     * Registers a hook that runs before every step of every stage.
     */
    fun beforeEveryStep(hook: StageHook) {
        beforeEveryStep.add(hook)
    }

    /**
     * Registers a hook that runs after every step of every stage.
     */
    fun afterEveryStep(hook: StageHook) {
        afterEveryStep.add(hook)
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
