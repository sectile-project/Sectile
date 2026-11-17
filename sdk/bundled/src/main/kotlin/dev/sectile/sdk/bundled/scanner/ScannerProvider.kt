package dev.sectile.sdk.bundled.scanner

import dev.sectile.kernel.Kernel
import dev.sectile.kernel.plugin.scope.provider.DependencyProvider
import dev.sectile.kernel.plugin.scope.provider.ProtectedDependencyProvider
import dev.sectile.sdk.scanner.PackageScanner

class ScannerProvider(
    override val type: Class<PackageScanner> = PackageScanner::class.java,
) : DependencyProvider<PackageScanner>,
    ProtectedDependencyProvider {

    override fun provide(): PackageScanner {
        val snapshot = Kernel.pluginCatalog.getFromCurrentScope()
            ?: error("No plugin snapshot available")

        return DefaultPackageScanner(
            defaultPackage = snapshot.mainClass.substringBeforeLast('.'),
            classLoader = Thread.currentThread().contextClassLoader,
            logger = Kernel.loggerFactory.createLogger(snapshot.metadata.id.value + " Scanner"),
        )
    }
}
