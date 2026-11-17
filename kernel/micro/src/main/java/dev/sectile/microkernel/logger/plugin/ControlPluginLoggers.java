package dev.sectile.microkernel.logger.plugin;

import dev.sectile.kernel.Kernel;
import dev.sectile.kernel.logger.Logger;
import dev.sectile.kernel.plugin.spi.lifecycle.PluginLifecycle;
import dev.sectile.microkernel.logger.WrappedLogger;
import java.util.HashMap;
import java.util.Map;
import org.jetbrains.annotations.Nullable;

public final class ControlPluginLoggers {
  private static final Map<PluginLifecycle, WrappedLogger> loggers = new HashMap<>();

  private ControlPluginLoggers() {
    throw new UnsupportedOperationException();
  }

  public static void flush(final PluginLifecycle plugin) {
    synchronized (ControlPluginLoggers.class) {
      final WrappedLogger wrappedLogger = loggers.remove(plugin);
      if (wrappedLogger == null
          || !(wrappedLogger.getLogger() instanceof PluginStartupLogger pluginStartupLogger)) {
        return;
      }

      pluginStartupLogger.flush();
      wrappedLogger.setLogger(pluginStartupLogger.getInitialLogger());
    }
  }

  public static void discard(final PluginLifecycle plugin) {
    synchronized (ControlPluginLoggers.class) {
      final WrappedLogger wrappedLogger = loggers.remove(plugin);
      if (wrappedLogger == null
          || !(wrappedLogger.getLogger() instanceof PluginStartupLogger pluginStartupLogger)) {
        return;
      }

      wrappedLogger.setLogger(pluginStartupLogger.getInitialLogger());
    }
  }

  public static Logger insert(final PluginLifecycle plugin, final String pluginName) {
    synchronized (ControlPluginLoggers.class) {
      if (loggers.containsKey(plugin)) {
        throw new IllegalStateException("Plugin " + pluginName + " already exists");
      }

      final WrappedLogger wrappedLogger =
          (WrappedLogger) Kernel.Companion.getLoggerFactory().createLogger(pluginName);
      wrappedLogger.setLogger(new PluginStartupLogger(wrappedLogger.getLogger()));
      loggers.put(plugin, wrappedLogger);
      return wrappedLogger;
    }
  }

  public static @Nullable Logger getLogger(final PluginLifecycle plugin) {
    synchronized (ControlPluginLoggers.class) {
      return loggers.get(plugin);
    }
  }
}
