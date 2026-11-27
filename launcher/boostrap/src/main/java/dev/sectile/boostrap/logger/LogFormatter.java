package dev.sectile.boostrap.logger;

import dev.sectile.kernel.logger.LogLevel;
import java.util.Arrays;
import java.util.TimeZone;

public final class LogFormatter {
  private static final String PLACEHOLDER = "%time%";
  private static final TimeZone TIME_ZONE = TimeZone.getDefault();

  private final char[] timeBuf = new char[8];
  private final StringBuilder prefixBuilder = new StringBuilder(32);
  private final String[] rawPrefixes = new String[LogLevel.getEntries().size()];
  private final String[] cachedPrefixes = new String[LogLevel.getEntries().size()];

  private long cachedSecond = Long.MIN_VALUE;
  private String cachedTime = "";

  public void format(final MutableLogEntry logEntry) {
    final String prefix = logEntry.prefix;
    if (prefix == null) {
      return;
    }

    final int placeholderIdx = prefix.indexOf(PLACEHOLDER);
    if (placeholderIdx < 0) {
      return;
    }

    final long second = Math.floorDiv(logEntry.timestamp, 1000L);
    if (second != cachedSecond) {
      cachedTime = formatTime(logEntry.timestamp);
      Arrays.fill(cachedPrefixes, null);
      cachedSecond = second;
    }

    final int level = logEntry.level;
    if (level >= 0 && level < cachedPrefixes.length) {
      final String cached = cachedPrefixes[level];
      if (cached != null && rawPrefixes[level] == prefix) {
        logEntry.prefix = cached;
        return;
      }
    }

    prefixBuilder.setLength(0);
    int from = 0;
    int idx = placeholderIdx;
    while (idx >= 0) {
      prefixBuilder.append(prefix, from, idx).append(cachedTime);
      from = idx + PLACEHOLDER.length();
      idx = prefix.indexOf(PLACEHOLDER, from);
    }
    prefixBuilder.append(prefix, from, prefix.length());

    final String formatted = prefixBuilder.toString();
    if (level >= 0 && level < cachedPrefixes.length) {
      rawPrefixes[level] = prefix;
      cachedPrefixes[level] = formatted;
    }
    logEntry.prefix = formatted;
  }

  private String formatTime(final long millis) {
    final long localSeconds = Math.floorDiv(millis + TIME_ZONE.getOffset(millis), 1000L);
    final long currentDaySeconds = Math.floorMod(localSeconds, 86400L);

    writePadded(timeBuf, 0, currentDaySeconds / 3600L); // HH
    timeBuf[2] = ':';
    writePadded(timeBuf, 3, (currentDaySeconds % 3600L) / 60L); // mm
    timeBuf[5] = ':';
    writePadded(timeBuf, 6, currentDaySeconds % 60L); // ss

    return new String(timeBuf);
  }

  private static void writePadded(final char[] buf, final int offset, long value) {
    for (int i = offset + 2 - 1; i >= offset; i--) {
      buf[i] = (char) ('0' + (value % 10));
      value /= 10;
    }
  }
}
