package dev.sectile.network.api.player.stage

/**
 * Registry of the extensions of the stage machine.
 *
 * A plugin obtains its scoped [StageHookRegistry] via `scope.stageHooks()`, registers a [StageHook]
 * on the steps it cares about, and decides whether the connection continues, is closed, or moves
 * to another stage. Everything happens on the public API: no transport type is involved.
 *
 * ```
 * scope.stageHooks().after(StepPoint(ConnectionStage.AUTHENTICATION, StageSteps.AUTHENTICATION_IDENTITY_TOKEN)) { ctx ->
 *     if (isBot(ctx.connection.getIp())) {
 *         StepDecision.Reject("client.general.disconnect.banned")
 *     } else {
 *         StepDecision.Continue
 *     }
 * }
 * ```
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
