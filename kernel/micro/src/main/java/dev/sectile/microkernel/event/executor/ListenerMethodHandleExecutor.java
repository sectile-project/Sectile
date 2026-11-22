package dev.sectile.microkernel.event.executor;

import dev.sectile.kernel.event.Cancellable;
import dev.sectile.kernel.event.dispatch.EventExecutor;
import dev.sectile.kernel.logger.Logger;
import dev.sectile.microkernel.plugin.classloading.PluginClassLoader;
import java.lang.invoke.MethodHandle;
import org.jetbrains.annotations.NotNull;

public final class ListenerMethodHandleExecutor implements EventExecutor {

  private final Logger logger;
  private final Object listener;
  private final boolean ignoreCancelled;
  private final MethodHandle methodHandle;
  private final ClassLoader pluginClassLoader;

  public ListenerMethodHandleExecutor(
      final Logger logger,
      final Object listener,
      final boolean ignoreCancelled,
      final MethodHandle methodHandle) {
    this.logger = logger;
    this.listener = listener;
    this.ignoreCancelled = ignoreCancelled;
    this.methodHandle = methodHandle;
    final ClassLoader loader = listener.getClass().getClassLoader();
    this.pluginClassLoader = loader instanceof PluginClassLoader ? loader : null;
  }

  @Override
  public void execute(final @NotNull Object event) {
    if (!ignoreCancelled
        && event instanceof Cancellable cancellable
        && cancellable.getCancelled()) {
      return;
    }
    final Thread thread = Thread.currentThread();
    final ClassLoader previous = pluginClassLoader != null ? thread.getContextClassLoader() : null;
    if (pluginClassLoader != null) {
      thread.setContextClassLoader(pluginClassLoader);
    }
    try {
      methodHandle.invoke(listener, event);
    } catch (final Throwable e) {
      logger.error("Error executing the listener %s", e, listener.getClass());
    } finally {
      if (pluginClassLoader != null) {
        thread.setContextClassLoader(previous);
      }
    }
  }
}
