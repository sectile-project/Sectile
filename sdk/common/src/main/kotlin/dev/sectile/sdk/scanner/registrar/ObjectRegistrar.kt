package dev.sectile.sdk.scanner.registrar

/**
 * A **Registrar** is a component responsible for:
 * - Registering items (classes, services, routes, plugins, listeners, etc.)
 * - Storing them in an internal registry (e.g., `Map`, `List`)
 * - Allowing retrieval or lookup by key (name, ID, type)
 *
 * Common examples:
 * - `ListenerRegistrar`: allows plugins to register listeners
 */
interface ObjectRegistrar<T : Any> {

    val targetType: Class<T>

    fun registrar(instance: T)
}
