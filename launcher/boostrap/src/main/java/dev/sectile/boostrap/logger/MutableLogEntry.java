package dev.sectile.boostrap.logger;

/**
 * A reusable data container for log entries.
 *
 * <p>By reusing these objects within the {@link LogRingBuffer}, the system avoids constant object
 * allocation/deallocation, significantly reducing GC pressure.
 */
public final class MutableLogEntry {
  public byte level;
  public long timestamp;

  public String prefix;
  public String message;
  public Throwable throwable;

  /**
   * Atomic sequencer used to coordinate access between producers and the consumer.
   *
   * <p>State logic:
   *
   * <ul>
   *   <li>Initial state of slot {@code i}: {@code i}
   *   <li>Published state of item {@code n}: {@code n + 1}
   *   <li>Available for reuse after consumption: {@code n + capacity}
   * </ul>
   */
  volatile long sequence;

  MutableLogEntry(long sequence) {
    this.sequence = sequence;
  }

  /**
   * Clears references to strings and throwables to prevent memory leaks while the slot is idle in
   * the ring.
   */
  public void clear() {
    this.timestamp = 0;
    this.level = 0;
    this.prefix = null;
    this.message = null;
    this.throwable = null;
  }
}
