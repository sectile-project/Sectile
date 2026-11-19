package dev.sectile.boostrap.logger;

import java.util.TimeZone;

public final class LogFormatter {
  private static final String PLACEHOLDER = "%time%";
  private static final TimeZone TIME_ZONE = TimeZone.getDefault();

  public void format(final MutableLogEntry logEntry) {
    if (logEntry.prefix.contains(PLACEHOLDER)) {
      logEntry.prefix = logEntry.prefix.replace(PLACEHOLDER, millisToTime(logEntry.timestamp));
    }
  }

  private static String millisToTime(final long millis) {
    final long localSeconds = (millis + TIME_ZONE.getOffset(millis)) / 1000;
    final long currentDaySeconds = Math.floorMod(localSeconds, 86400);

    final char[] buf = new char[8];
    writePadded(buf, 0, currentDaySeconds / 3600); // HH
    buf[2] = ':';
    writePadded(buf, 3, (currentDaySeconds % 3600) / 60); // mm
    buf[5] = ':';
    writePadded(buf, 6, currentDaySeconds % 60); // ss

    return new String(buf);
  }

  private static void writePadded(final char[] buf, final int offset, long value) {
    for (int i = offset + 2 - 1; i >= offset; i--) {
      buf[i] = (char) ('0' + (value % 10));
      value /= 10;
    }
  }
}
