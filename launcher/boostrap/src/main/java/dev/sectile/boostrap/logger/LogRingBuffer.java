package dev.sectile.boostrap.logger;

import dev.sectile.kernel.logger.LogLevel;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A bounded Multi-Producer Single-Consumer (MPSC) Ring Buffer.
 *
 * <p>This implementation uses a sequence-based synchronization approach similar to the LMAX
 * Disruptor to allow multiple threads to log concurrently without heavy locking.
 */
public final class LogRingBuffer {
  private final int mask;
  private final MutableLogEntry[] buffer;

  // Multi-producer cursor.
  private final AtomicLong writeIndex = new AtomicLong(0L);

  // Single-consumer cursor.
  private long readIndex = 0L;
  private volatile Thread consumerThread;
  private final AtomicBoolean consumerParked = new AtomicBoolean(false);

  private final AtomicLong dropped = new AtomicLong(0L);

  private static final int CAPACITY = 2048;

  public LogRingBuffer() {
    this.mask = CAPACITY - 1;
    this.buffer = new MutableLogEntry[CAPACITY];

    for (int i = 0; i < CAPACITY; i++) {
      buffer[i] = new MutableLogEntry(i);
    }
  }

  /**
   * Pushes a new entry into the buffer.
   *
   * <p><b>Concurrency:</b> Thread-safe for multiple producers.
   *
   * @return {@code true} if successful, {@code false} if the buffer is full.
   */
  public boolean push(
      final @NotNull LogLevel level,
      final @NotNull String prefix,
      final @NotNull String message,
      final @Nullable Throwable throwable) {
    while (true) {
      final long sequence = writeIndex.get();
      final MutableLogEntry entry = buffer[(int) (sequence & mask)];
      final long entrySequence = entry.sequence; // volatile read
      final long diff = entrySequence - sequence;

      if (diff == 0L) {
        // Free slot. Only one producer can win it.
        if (writeIndex.compareAndSet(sequence, sequence + 1L)) {
          entry.level = (byte) level.ordinal();
          entry.timestamp = System.currentTimeMillis();
          entry.prefix = prefix;
          entry.message = message;
          entry.throwable = throwable;

          // Volatile write: publishes prefix/message/throwable to consumer.
          entry.sequence = sequence + 1L;
          if (consumerParked.get() && consumerParked.compareAndSet(true, false)) {
            LockSupport.unpark(consumerThread);
          }
          return true;
        }
      } else if (diff < 0L) {
        // Producer wrapped around the ring and consumer has not yet freed this slot.
        dropped.incrementAndGet();
        return false;
      } else {
        // Another producer or consumer moved the state. Retry.
        Thread.yield();
      }
    }
  }

  /**
   * Polls the next available entry from the buffer.
   *
   * <p><b>Concurrency:</b> Only one consumer thread should call this.
   *
   * @return The entry if available, {@code null} otherwise.
   */
  public MutableLogEntry poll() {
    final MutableLogEntry entry = buffer[(int) (readIndex & mask)];
    final long expected = readIndex + 1L;

    // Volatile read: observes producer publication.
    if (entry.sequence == expected) {
      readIndex++;
      return entry;
    }

    return null;
  }

  /**
   * Releases a consumed slot back to the ring, making it available for producers again.
   *
   * @param entry The entry previously obtained via {@link #poll()}.
   */
  public void release(MutableLogEntry entry) {
    final long publishedSequence = entry.sequence;
    entry.clear();

    // Volatile write: marks the slot as free for the next ring iteration.
    entry.sequence = (publishedSequence - 1L) + CAPACITY;
  }

  public boolean isEmpty() {
    return writeIndex.get() == readIndex;
  }

  public long size() {
    final long size = writeIndex.get() - readIndex;
    if (size <= 0L) {
      return 0L;
    }
    return Math.min(size, CAPACITY);
  }

  public long droppedCount() {
    return dropped.get();
  }

  void setConsumerThread(final Thread consumerThread) {
    this.consumerThread = consumerThread;
  }

  void markConsumerParked() {
    consumerParked.set(true);
  }

  void clearConsumerParked() {
    consumerParked.set(false);
  }
}
