package dev.sectile.microkernel.event.executor;

import dev.sectile.kernel.event.Cancellable;
import dev.sectile.kernel.event.dispatch.EventExecutor;
import dev.sectile.microkernel.Microkernel;
import dev.sectile.microkernel.plugin.classloading.PluginClassLoader;
import kotlin.Unit;
import kotlin.jvm.functions.Function1;
import org.jetbrains.annotations.NotNull;

public final class ListenerConsumerFactory {

  public static <T> EventExecutor create(
      final Function1<? super T, Unit> consumer, final boolean ignoreCancelled) {
    return new LambdaEventExecutor<>(consumer, ignoreCancelled);
  }

  private static final class LambdaEventExecutor<T> implements EventExecutor {
    private final Function1<? super T, Unit> consumer;
    private final boolean ignoreCancelled;
    private final ClassLoader pluginClassLoader;

    private LambdaEventExecutor(
        final Function1<? super T, Unit> consumer, final boolean ignoreCancelled) {
      this.consumer = consumer;
      this.ignoreCancelled = ignoreCancelled;
      final ClassLoader loader = consumer.getClass().getClassLoader();
      this.pluginClassLoader = loader instanceof PluginClassLoader ? loader : null;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void execute(@NotNull final Object event) {
      if (!ignoreCancelled
          && event instanceof Cancellable cancellable
          && cancellable.getCancelled()) {
        return;
      }
      final Thread thread = Thread.currentThread();
      final ClassLoader previous =
          pluginClassLoader != null ? thread.getContextClassLoader() : null;
      if (pluginClassLoader != null) {
        thread.setContextClassLoader(pluginClassLoader);
      }
      try {
        consumer.invoke((T) event);
      } catch (Throwable e) {
        Microkernel.Companion.getInstance()
            .getRootLogger()
            .error("ListenerConsumerExecutor execute exception", e);
      } finally {
        if (pluginClassLoader != null) {
          thread.setContextClassLoader(previous);
        }
      }
    }
  }
}
