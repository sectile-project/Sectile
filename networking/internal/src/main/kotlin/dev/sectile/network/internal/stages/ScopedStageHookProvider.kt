package dev.sectile.network.internal.stages

import dev.sectile.kernel.plugin.scope.provider.CloseableDependencyProvider
import dev.sectile.kernel.plugin.scope.provider.ProtectedDependencyProvider
import dev.sectile.network.api.player.stage.StageHook
import dev.sectile.network.api.player.stage.StageHookRegistration
import dev.sectile.network.api.player.stage.StageHookRegistry
import dev.sectile.network.api.player.stage.StepContext
import dev.sectile.network.api.player.stage.StepDecision
import dev.sectile.network.api.player.stage.StepPoint

class ScopedStageHookProvider(
    private val delegate: StageHookRegistry = StageHooks,
    override val type: Class<StageHookRegistry> = StageHookRegistry::class.java,
) : StageHookRegistry,
    CloseableDependencyProvider<StageHookRegistry>,
    ProtectedDependencyProvider {

    private val registrations = linkedSetOf<StageHookRegistration>()

    override fun before(point: StepPoint, hook: StageHook): StageHookRegistration = track(delegate.before(point, wrap(hook)))

    override fun after(point: StepPoint, hook: StageHook): StageHookRegistration = track(delegate.after(point, wrap(hook)))

    override fun beforeEveryStep(hook: StageHook): StageHookRegistration = track(delegate.beforeEveryStep(wrap(hook)))

    override fun afterEveryStep(hook: StageHook): StageHookRegistration = track(delegate.afterEveryStep(wrap(hook)))

    override fun provide(): StageHookRegistry = this

    override fun close() {
        val snapshot = synchronized(registrations) {
            registrations.toList().also { registrations.clear() }
        }
        for (registration in snapshot) {
            registration.unregister()
        }
    }

    private fun wrap(hook: StageHook): StageHook = ScopedStageHook(
        delegate = hook,
        classLoader = hook.javaClass.classLoader ?: Thread.currentThread().contextClassLoader,
    )

    private fun track(raw: StageHookRegistration): StageHookRegistration {
        lateinit var handle: StageHookRegistration
        handle = object : StageHookRegistration {
            override fun unregister() {
                val removed = synchronized(registrations) {
                    registrations.remove(handle)
                }
                if (removed) {
                    raw.unregister()
                }
            }
        }
        synchronized(registrations) {
            registrations.add(handle)
        }
        return handle
    }

    private class ScopedStageHook(
        private val delegate: StageHook,
        private val classLoader: ClassLoader?,
    ) : StageHook {

        override fun onStep(context: StepContext): StepDecision {
            val loader = classLoader
            if (loader == null) {
                return delegate.onStep(context)
            }
            val thread = Thread.currentThread()
            val previous = thread.contextClassLoader
            thread.contextClassLoader = loader
            try {
                return delegate.onStep(context)
            } finally {
                thread.contextClassLoader = previous
            }
        }
    }
}
