package dev.sectile.boostrap.logger.file;

import java.io.*;
import java.util.TimeZone;
import java.util.zip.GZIPOutputStream;

public final class LogCompressor {

  private static final int BUFFER_SIZE = 128 * 1024;

  public static void compress(final File file, final boolean compress) throws IOException {
    if (!file.isFile()) {
      throw new IOException("File not found: " + file);
    }

    final String name = millisToFileName(file.lastModified());
    final File parent = file.getParentFile();

    if (!compress) {
      final File target = new File(parent, name + ".log");
      if (target.exists() && !target.delete()) {
        throw new IOException("Cannot replace: " + target);
      }
      if (!file.renameTo(target)) {
        throw new IOException("Failed to rename " + file + " to " + target);
      }
      return;
    }

    final File gzip = new File(parent, name + ".log.gz");
    final byte[] buffer = new byte[BUFFER_SIZE];

    try (FileInputStream in = new FileInputStream(file);
        BufferedOutputStream bos =
            new BufferedOutputStream(new FileOutputStream(gzip), BUFFER_SIZE);
        GZIPOutputStream out = new GZIPOutputStream(bos, BUFFER_SIZE)) {
      int read;
      while ((read = in.read(buffer)) != -1) {
        out.write(buffer, 0, read);
      }
    } catch (IOException e) {
      gzip.delete(); // Remove corrupted gzip
      throw new IOException("Failed to create GZIP file", e);
    }

    if (!file.delete()) {
      throw new IOException("Failed to delete original: " + file);
    }
  }

  private static final TimeZone TIME_ZONE = TimeZone.getDefault();

  private static String millisToFileName(final long millis) {
    final long SEC_PER_MIN = 60;
    final long SEC_PER_HOUR = 3600;
    final long SEC_PER_DAY = 86400;

    final long totalSeconds = Math.floorDiv(millis + TIME_ZONE.getOffset(millis), 1000L);
    final long currentDaySeconds = Math.floorMod(totalSeconds, SEC_PER_DAY);

    final long hour = currentDaySeconds / SEC_PER_HOUR;
    final long minute = (currentDaySeconds % SEC_PER_HOUR) / SEC_PER_MIN;
    final long second = currentDaySeconds % SEC_PER_MIN;

    long totalDays = Math.floorDiv(totalSeconds, SEC_PER_DAY);

    // Hinnant
    totalDays += 719468;
    final long era = (totalDays >= 0 ? totalDays : totalDays - 146096) / 146097;
    final long doe = totalDays - era * 146097;
    final long yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365;
    long y = yoe + era * 400;
    final long doy = doe - (365 * yoe + yoe / 4 - yoe / 100);
    final long mp = (5 * doy + 2) / 153;
    final long d = doy - (153 * mp + 2) / 5 + 1;
    final long m = mp < 10 ? mp + 3 : mp - 9;
    y += (m <= 2 ? 1 : 0);

    final char[] buf = new char[19];
    writePadded(buf, 0, 4, y); // yyyy
    buf[4] = '-';
    writePadded(buf, 5, 2, m); // MM
    buf[7] = '-';
    writePadded(buf, 8, 2, d); // dd
    buf[10] = '_';
    writePadded(buf, 11, 2, hour); // HH
    buf[13] = '-';
    writePadded(buf, 14, 2, minute); // mm
    buf[16] = '-';
    writePadded(buf, 17, 2, second); // ss

    return new String(buf);
  }

  private static void writePadded(
      final char[] buf, final int offset, final int length, long value) {
    for (int i = offset + length - 1; i >= offset; i--) {
      buf[i] = (char) ('0' + (value % 10));
      value /= 10;
    }
  }
}
