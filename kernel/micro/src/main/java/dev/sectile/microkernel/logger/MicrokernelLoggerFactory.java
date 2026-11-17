package dev.sectile.microkernel.logger;

import dev.sectile.kernel.logger.Logger;
import dev.sectile.kernel.plugin.spi.lifecycle.PluginBoostrap;
import dev.sectile.kernel.plugin.spi.lifecycle.PluginLifecycle;
import dev.sectile.microkernel.logger.plugin.ControlPluginLoggers;
import dev.sectile.microkernel.plugin.classloading.PluginClassLoader;
import org.jetbrains.annotations.NotNull;

public class MicrokernelLoggerFactory extends SimpleLoggerFactory {

  public MicrokernelLoggerFactory(final @NotNull Logger defaultLogger) {
    super(defaultLogger);
  }

  @Override
  public @NotNull Logger acquire(final @NotNull Object owner) {
    if (owner instanceof PluginLifecycle pluginLifecycle) {
      final Logger logger = ControlPluginLoggers.getLogger(pluginLifecycle);
      if (logger != null) {
        return logger;
      }

      final PluginClassLoader pluginClassLoader =
          (PluginClassLoader) Thread.currentThread().getContextClassLoader();
      return super.createLogger('[' + pluginClassLoader.getName() + ']');
    }

    if (owner instanceof PluginBoostrap) {
      final PluginClassLoader pluginClassLoader =
          (PluginClassLoader) Thread.currentThread().getContextClassLoader();
      return super.createLogger('[' + pluginClassLoader.getName() + " - BOOTSTRAP]");
    }

    return super.acquire(owner);
  }
}
