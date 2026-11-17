package dev.sectile.sdk.scanner.registrar

import dev.sectile.kernel.Kernel
import dev.sectile.sdk.extension.eventbus

object ListenerRegistrar : ObjectRegistrar<Any> {

    override val targetType = Any::class.java

    override fun registrar(instance: Any) {
        val scope = Kernel.pluginScopeFactory.acquire()
        scope.eventbus().subscribe(instance)
    }
}
