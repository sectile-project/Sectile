package dev.sectile.sdk.scanner

import dev.sectile.sdk.scanner.registrar.ObjectRegistrar

data class ScanResult(
    val results: Collection<PackageScanResult>,
) {

    data class PackageScanResult(
        val classes: Collection<Class<*>>,
        val packageName: String,
        val registrar: ObjectRegistrar<*>,
    )

    fun amountClasses(): Int {
        var total = 0
        results.forEach { result -> total += result.classes.size }
        return total
    }
}
