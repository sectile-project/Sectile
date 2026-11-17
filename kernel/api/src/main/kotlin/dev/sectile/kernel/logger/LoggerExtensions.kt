package dev.sectile.kernel.logger

import dev.sectile.kernel.Kernel
import kotlin.reflect.KClass

fun Any.logger(): Logger = Kernel.loggerFactory.acquire(this)

fun KClass<*>.logger(): Logger = Kernel.loggerFactory.acquire(this)

inline fun <reified T : Any> logger(): Logger = Kernel.loggerFactory.acquire(T::class)
