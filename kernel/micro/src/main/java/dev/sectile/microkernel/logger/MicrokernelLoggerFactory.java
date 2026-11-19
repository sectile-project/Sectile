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

      final PluginClassLoader pluginClassLoader = resolvePluginClassLoader(owner);
      if (pluginClassLoader != null) {
        return super.createLogger('[' + pluginClassLoader.getName() + ']');
      }
      return super.acquire(owner);
    }

    if (owner instanceof PluginBoostrap) {
      final PluginClassLoader pluginClassLoader = resolvePluginClassLoader(owner);
      if (pluginClassLoader != null) {
        return super.createLogger('[' + pluginClassLoader.getName() + " - BOOTSTRAP]");
      }
      return super.acquire(owner);
    }

    return super.acquire(owner);
  }

  private static PluginClassLoader resolvePluginClassLoader(final @NotNull Object owner) {
    if (owner.getClass().getClassLoader() instanceof PluginClassLoader pluginClassLoader) {
      return pluginClassLoader;
    }
    if (Thread.currentThread().getContextClassLoader()
        instanceof PluginClassLoader pluginClassLoader) {
      return pluginClassLoader;
    }
    return null;
  }
}
