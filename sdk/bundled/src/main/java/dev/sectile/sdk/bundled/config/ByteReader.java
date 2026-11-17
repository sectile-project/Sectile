package dev.sectile.sdk.bundled.config;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;

public class ByteReader {

  public static final VarHandle LONG_LE =
      MethodHandles.byteArrayViewVarHandle(long[].class, ByteOrder.LITTLE_ENDIAN);
  public static final VarHandle INT_LE =
      MethodHandles.byteArrayViewVarHandle(int[].class, ByteOrder.LITTLE_ENDIAN);

  public static final int INT_NULL = 0x6C6C756E; // "null" in LE
  public static final int INT_TRUE = 0x65757274; // "true" in LE
  public static final int INT_FALS = 0x736C6166; // "fals" in LE

  // RFC 8259 whitespace bitmask: ' ' (0x20), '\t' (0x09), '\n' (0x0A), '\r' (0x0D)
  public static final long JSON_SPACE_MASK =
      (1L << ' ') | (1L << '\t') | (1L << '\n') | (1L << '\r');

  public byte[] buf;
  public int pos;
  public int end;
  public int lineNumber = 1;
  public final StringBuilder sb = new StringBuilder(256);

  public ByteReader() {}

  public ByteReader(final byte[] buf) {
    reset(buf, 0, buf.length);
  }

  public void reset(final byte[] buf, final int offset, final int length) {
    this.buf = buf;
    this.end = offset + length;
    this.lineNumber = 1;
    // Skip UTF-8 BOM (0xEF, 0xBB, 0xBF) if present
    if (length >= 3
        && (buf[offset] & 0xFF) == 0xEF
        && (buf[offset + 1] & 0xFF) == 0xBB
        && (buf[offset + 2] & 0xFF) == 0xBF) {
      this.pos = offset + 3;
    } else {
      this.pos = offset;
    }
  }

  public static long getLongLE(final byte[] buf, final int offset) {
    return (long) LONG_LE.get(buf, offset);
  }

  public static int getIntLE(final byte[] buf, final int offset) {
    return (int) INT_LE.get(buf, offset);
  }

  public final byte next() {
    return buf[pos++];
  }

  public final boolean isLineEnd(final byte b) {
    return b == '\n' || b == '\r';
  }

  public final void skipJsonWhitespace() {
    final int end = this.end;
    int p = this.pos;
    if (p >= end) return;

    final byte[] buf = this.buf;
    byte b = buf[p];
    if (b > ' ') return;

    // 1. Fast-path single space (e.g. after ':' or ',')
    if (b == ' ') {
      if (p + 1 < end && buf[p + 1] > ' ') {
        this.pos = p + 1;
        return;
      }
      p++;
    }

    // 2. Main loop: handle newline + SWAR 8-byte space blocks
    while (p < end) {
      b = buf[p];
      if (b == '\n') {
        this.lineNumber++;
        p++;
      } else if (b == '\r') {
        p++;
        if (p < end && buf[p] == '\n') {
          this.lineNumber++;
          p++;
        }
      } else if (b != ' ' && b != '\t') {
        break;
      }

      // Now we are positioned right at the start of the line's indentation spaces!
      while (p + 8 <= end) {
        final long v = getLongLE(buf, p);
        if (v == 0x2020202020202020L) {
          p += 8;
          continue;
        }
        // Count how many leading bytes in `v` are ' ' (0x20) in 1 CPU instruction!
        final long xor = v ^ 0x2020202020202020L;
        final int leadingSpaces = Long.numberOfTrailingZeros(xor) >>> 3;
        p += leadingSpaces;
        break;
      }

      if (p < end && buf[p] > ' ') {
        break;
      }
      while (p < end && (buf[p] == ' ' || buf[p] == '\t')) {
        p++;
      }
    }
    this.pos = p;
  }

  public static int indexOfNewline(final byte[] buf, int p, final int end) {
    final int limit = end - 8;
    while (p <= limit) {
      final long v = getLongLE(buf, p);
      final long x = v ^ 0x0A0A0A0A0A0A0A0AL; // '\n'
      final long match = (x - 0x0101010101010101L) & ~x & 0x8080808080808080L;
      if (match != 0) {
        return p + (Long.numberOfTrailingZeros(match) >>> 3);
      }
      p += 8;
    }
    while (p < end && buf[p] != '\n') {
      p++;
    }
    return p;
  }

  public final void appendUtf8(final byte b) {
    if (b >= 0) {
      sb.append((char) b);
    } else {
      appendUtf8MultiByte(b);
    }
  }

  private void appendUtf8MultiByte(final byte b) {
    final byte[] buf = this.buf;
    int p = this.pos;
    final int end = this.end;

    if ((b & 0xE0) == 0xC0) {
      if (p >= end || (buf[p] & 0xC0) != 0x80 || (b & 0x1E) == 0) {
        throw error("UTF-8 truncated or malformed");
      }
      final int b2 = buf[p++] & 0x3F;
      this.pos = p;
      sb.append((char) (((b & 0x1F) << 6) | b2));
    } else if ((b & 0xF0) == 0xE0) {
      if (p + 1 >= end || (buf[p] & 0xC0) != 0x80 || (buf[p + 1] & 0xC0) != 0x80) {
        throw error("UTF-8 truncated or malformed");
      }
      final int b2 = buf[p++] & 0x3F;
      final int b3 = buf[p++] & 0x3F;
      final int cp = ((b & 0x0F) << 12) | (b2 << 6) | b3;
      if (cp < 0x800 || (cp >= 0xD800 && cp <= 0xDFFF)) {
        throw error("UTF-8 overlong or surrogate sequence");
      }
      this.pos = p;
      sb.append((char) cp);
    } else if ((b & 0xF8) == 0xF0) {
      if (p + 2 >= end
          || (buf[p] & 0xC0) != 0x80
          || (buf[p + 1] & 0xC0) != 0x80
          || (buf[p + 2] & 0xC0) != 0x80) {
        throw error("UTF-8 truncated or malformed");
      }
      final int b2 = buf[p++] & 0x3F;
      final int b3 = buf[p++] & 0x3F;
      final int b4 = buf[p++] & 0x3F;
      final int cp = ((b & 0x07) << 18) | (b2 << 12) | (b3 << 6) | b4;
      if (cp < 0x10000 || cp > 0x10FFFF) {
        throw error("UTF-8 codepoint out of range");
      }
      this.pos = p;
      sb.appendCodePoint(cp);
    } else {
      throw error("invalid UTF-8 byte");
    }
  }

  public IllegalArgumentException error(final String msg) {
    return new IllegalArgumentException("Parse error (line " + lineNumber + "): " + msg);
  }
}
