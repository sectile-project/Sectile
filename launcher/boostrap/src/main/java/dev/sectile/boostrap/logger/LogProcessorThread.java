package dev.sectile.boostrap.logger;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class LogProcessorThread extends Thread {
  private final @NotNull LogRingBuffer ringBuffer;
  private final long intervalNanos;

  private final @NotNull LogFormatter logFormatter;
  private final @Nullable LogAppender consoleAppender;
  private final @Nullable LogAppender fileAppender;

  private volatile boolean running = true;

  public LogProcessorThread(
      @NotNull LogRingBuffer ringBuffer,
      long intervalSeconds,
      final @Nullable LogAppender consoleAppender,
      final @Nullable LogAppender fileAppender,
      final @NotNull LogFormatter logFormatter) {
    super("Log-Processor-Thread");
    this.ringBuffer = ringBuffer;
    this.intervalNanos = TimeUnit.SECONDS.toNanos(intervalSeconds <= 0 ? 1 : intervalSeconds);
    this.consoleAppender = consoleAppender;
    this.fileAppender = fileAppender;
    this.logFormatter = logFormatter;
    this.setDaemon(true);
  }

  @Override
  public void run() {
    ringBuffer.setConsumerThread(this);
    long nextFlushAt = System.nanoTime() + intervalNanos;

    while (running || !ringBuffer.isEmpty()) {
      int processed = 0;
      MutableLogEntry entry;

      while ((entry = ringBuffer.poll()) != null) {
        try {
          logFormatter.format(entry);
          handleEntry(entry);
        } catch (Exception e) {
          System.err.println("[LogProcessor] Error appending log entry");
          e.printStackTrace(System.err);
        } finally {
          ringBuffer.release(entry);
        }
        processed++;
      }

      flushAppender(consoleAppender);
      final long now = System.nanoTime();

      if (now >= nextFlushAt) {
        flushAppender(fileAppender);
        nextFlushAt = now + intervalNanos;
      }

      if (!running) break;

      if (processed == 0) {
        long waitNanos = nextFlushAt - System.nanoTime();
        if (waitNanos > 0) {
          ringBuffer.markConsumerParked();
          try {
            if (running && ringBuffer.isEmpty()) {
              LockSupport.parkNanos(this, waitNanos);
            }
          } finally {
            ringBuffer.clearConsumerParked();
          }
        }
      }
    }

    flushAppenders();
    closeAppenders();
  }

  private void handleEntry(MutableLogEntry entry) throws IOException {
    if (consoleAppender != null) {
      consoleAppender.append(entry);
    }
    if (fileAppender != null) {
      fileAppender.append(entry);
    }
  }

  private void flushAppenders() {
    flushAppender(fileAppender);
    flushAppender(consoleAppender);
  }

  private void flushAppender(@Nullable LogAppender consoleAppender) {
    try {
      if (consoleAppender != null) consoleAppender.flush();
    } catch (Exception ignored) {
    }
  }

  private void closeAppenders() {
    try {
      if (fileAppender != null) fileAppender.close();
    } catch (Exception ignored) {
    }
    try {
      if (consoleAppender != null) consoleAppender.close();
    } catch (Exception ignored) {
    }
  }

  public void shutdown() {
    this.running = false;
    LockSupport.unpark(this);
  }
}
