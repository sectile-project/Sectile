package dev.sectile.sdk.bundled.config;

import java.nio.charset.StandardCharsets;

public final class StringDecodeUtil {

  // 2048-entry cache for 1..8 byte keys (power of 2)
  private static final int CACHE8_MASK = 2047;
  private static final KeyCacheEntry8[] KEY_CACHE_8 = new KeyCacheEntry8[2048];

  // 2048-entry cache for 9..16 byte keys (power of 2)
  private static final int CACHE16_MASK = 2047;
  private static final KeyCacheEntry16[] KEY_CACHE_16 = new KeyCacheEntry16[2048];

  private static final class KeyCacheEntry8 {
    final long word;
    final int len;
    final String key;

    KeyCacheEntry8(final long word, final int len, final String key) {
      this.word = word;
      this.len = len;
      this.key = key;
    }
  }

  private static final class KeyCacheEntry16 {
    final long word0;
    final long word1;
    final int len;
    final String key;

    KeyCacheEntry16(final long word0, final long word1, final int len, final String key) {
      this.word0 = word0;
      this.word1 = word1;
      this.len = len;
      this.key = key;
    }
  }

  private StringDecodeUtil() {}

  /**
   * Interns 1..16 byte ASCII keys and pre-warms String.hashCode() so Map.put() is O(1) without
   * hashing.
   */
  public static String internAsciiKey(final byte[] buf, final int start, final int len) {
    if (len == 0) return "";
    if (len <= 8 && start + 8 <= buf.length) {
      final long mask = len == 8 ? -1L : (1L << (len << 3)) - 1L;
      final long word = ByteReader.getLongLE(buf, start) & mask;
      final int idx = ((int) (word ^ (word >>> 32))) & CACHE8_MASK;
      final KeyCacheEntry8 entry = KEY_CACHE_8[idx];
      if (entry != null && entry.word == word && entry.len == len) {
        return entry.key;
      }
      final String str = new String(buf, start, len, StandardCharsets.ISO_8859_1);
      str.hashCode(); // Pre-cache String.hash field for Map.put()!
      KEY_CACHE_8[idx] = new KeyCacheEntry8(word, len, str);
      return str;
    }
    if (len <= 16 && start + 16 <= buf.length) {
      final long word0 = ByteReader.getLongLE(buf, start);
      final int rem = len - 8;
      final long mask1 = rem == 8 ? -1L : (1L << (rem << 3)) - 1L;
      final long word1 = ByteReader.getLongLE(buf, start + 8) & mask1;
      final long mix = word0 ^ word1;
      final int idx = ((int) (mix ^ (mix >>> 32))) & CACHE16_MASK;
      final KeyCacheEntry16 entry = KEY_CACHE_16[idx];
      if (entry != null && entry.word0 == word0 && entry.word1 == word1 && entry.len == len) {
        return entry.key;
      }
      final String str = new String(buf, start, len, StandardCharsets.ISO_8859_1);
      str.hashCode(); // Pre-cache String.hash field for Map.put()!
      KEY_CACHE_16[idx] = new KeyCacheEntry16(word0, word1, len, str);
      return str;
    }
    return new String(buf, start, len, StandardCharsets.ISO_8859_1);
  }

  /** Fused JSON key + colon reader. Reads `"key":` and skips any whitespace after `:`. */
  public static String readJsonKeyAndColon(final ByteReader r) {
    final byte[] buf = r.buf;
    final int end = r.end;
    final int start = ++r.pos; // Consume opening '"'

    if (start + 16 <= end) {
      final long v0 = ByteReader.getLongLE(buf, start);
      final long xQuote0 = v0 ^ 0x2222222222222222L;
      final long mQuote0 = (xQuote0 - 0x0101010101010101L) & ~xQuote0 & 0x8080808080808080L;

      // Fast-path 1: Key is 0..7 bytes long
      if (mQuote0 != 0) {
        final long xSlash0 = v0 ^ 0x5C5C5C5C5C5C5C5CL;
        final long mSlash0 = (xSlash0 - 0x0101010101010101L) & ~xSlash0;
        final long mCtrl0 = (v0 - 0x2020202020202020L) & ~v0;
        // Ensure no backslash, control char, or non-ASCII byte appears BEFORE the quote
        final long badMask = (mSlash0 | mCtrl0 | v0) & 0x8080808080808080L;
        if ((badMask == 0)
            || (Long.numberOfTrailingZeros(mQuote0) < Long.numberOfTrailingZeros(badMask))) {
          final int len = Long.numberOfTrailingZeros(mQuote0) >>> 3;
          final String key = len == 0 ? "" : internKeyFromWord8(buf, start, len, v0);
          consumeColonFast(r, buf, start + len + 1, end);
          return key;
        }
      } else {
        // Check if first 8 bytes are clean ASCII without escapes or control chars
        final long xSlash0 = v0 ^ 0x5C5C5C5C5C5C5C5CL;
        final long mSlash0 = (xSlash0 - 0x0101010101010101L) & ~xSlash0;
        final long mCtrl0 = (v0 - 0x2020202020202020L) & ~v0;
        if (((mSlash0 | mCtrl0 | v0) & 0x8080808080808080L) == 0) {
          // Fast-path 2: Key is 8..15 bytes long!
          final long v1 = ByteReader.getLongLE(buf, start + 8);
          final long xQuote1 = v1 ^ 0x2222222222222222L;
          final long mQuote1 = (xQuote1 - 0x0101010101010101L) & ~xQuote1 & 0x8080808080808080L;
          if (mQuote1 != 0) {
            final long xSlash1 = v1 ^ 0x5C5C5C5C5C5C5C5CL;
            final long mSlash1 = (xSlash1 - 0x0101010101010101L) & ~xSlash1;
            final long mCtrl1 = (v1 - 0x2020202020202020L) & ~v1;
            final long badMask1 = (mSlash1 | mCtrl1 | v1) & 0x8080808080808080L;
            if ((badMask1 == 0)
                || (Long.numberOfTrailingZeros(mQuote1) < Long.numberOfTrailingZeros(badMask1))) {
              final int rem = Long.numberOfTrailingZeros(mQuote1) >>> 3;
              final int totalLen = 8 + rem;
              final String key =
                  rem == 0
                      ? internKeyFromWord8(buf, start, 8, v0)
                      : internKeyFromWord16(buf, start, totalLen, v0, v1, rem);
              consumeColonFast(r, buf, start + totalLen + 1, end);
              return key;
            }
          }
        }
      }
    }

    r.pos = start - 1;
    final String key = readDoubleQuoted(r, true, true);
    consumeColonFast(r, buf, r.pos, end);
    return key;
  }

  private static String internKeyFromWord8(
      final byte[] buf, final int start, final int len, final long v0) {
    final long mask = len == 8 ? -1L : (1L << (len << 3)) - 1L;
    final long word = v0 & mask;
    final int idx = ((int) (word ^ (word >>> 32))) & CACHE8_MASK;
    final KeyCacheEntry8 entry = KEY_CACHE_8[idx];
    if (entry != null && entry.word == word && entry.len == len) {
      return entry.key;
    }
    final String str = new String(buf, start, len, StandardCharsets.ISO_8859_1);
    str.hashCode();
    KEY_CACHE_8[idx] = new KeyCacheEntry8(word, len, str);
    return str;
  }

  private static String internKeyFromWord16(
      final byte[] buf,
      final int start,
      final int totalLen,
      final long v0,
      final long v1,
      final int rem) {
    final long mask1 = (1L << (rem << 3)) - 1L;
    final long word1 = v1 & mask1;
    final long mix = v0 ^ word1;
    final int idx = ((int) (mix ^ (mix >>> 32))) & CACHE16_MASK;
    final KeyCacheEntry16 entry = KEY_CACHE_16[idx];
    if (entry != null && entry.word0 == v0 && entry.word1 == word1 && entry.len == totalLen) {
      return entry.key;
    }
    final String str = new String(buf, start, totalLen, StandardCharsets.ISO_8859_1);
    str.hashCode();
    KEY_CACHE_16[idx] = new KeyCacheEntry16(v0, word1, totalLen, str);
    return str;
  }

  private static void consumeColonFast(final ByteReader r, final byte[] buf, int p, final int end) {
    if (p + 1 < end && buf[p] == ':') {
      final byte next = buf[p + 1];
      if (next > ' ') {
        r.pos = p + 1;
        return;
      }
      if (next == ' ' && p + 2 < end && buf[p + 2] > ' ') {
        r.pos = p + 2;
        return;
      }
      r.pos = p + 1;
      r.skipJsonWhitespace();
      return;
    }
    r.pos = p;
    r.skipJsonWhitespace();
    if (r.pos >= end || buf[r.pos] != ':') {
      throw r.error("se esperaba ':' después de la clave");
    }
    r.pos++;
    r.skipJsonWhitespace();
  }

  public static String decodeSlice(final byte[] buf, final int start, final int end) {
    final int len = end - start;
    boolean ascii = true;
    int p = start;
    final int limit = end - 8;
    while (p <= limit) {
      if ((ByteReader.getLongLE(buf, p) & 0x8080808080808080L) != 0) {
        ascii = false;
        break;
      }
      p += 8;
    }
    if (ascii) {
      while (p < end) {
        if (buf[p++] < 0) {
          ascii = false;
          break;
        }
      }
    }
    return new String(
        buf, start, len, ascii ? StandardCharsets.ISO_8859_1 : StandardCharsets.UTF_8);
  }

  public static String readDoubleQuoted(
      final ByteReader r, final boolean internKey, final boolean strictJson) {
    final byte[] buf = r.buf;
    final int end = r.end;
    final int start = ++r.pos; // Consume opening '"'
    int p = start;

    final int limit = end - 8;
    while (p <= limit) {
      final long v = ByteReader.getLongLE(buf, p);
      if ((v & 0x8080808080808080L) != 0) {
        break;
      }
      final long xQuote = v ^ 0x2222222222222222L;
      final long xSlash = v ^ 0x5C5C5C5C5C5C5C5CL;
      final long mQuote = (xQuote - 0x0101010101010101L) & ~xQuote;
      final long mSlash = (xSlash - 0x0101010101010101L) & ~xSlash;
      final long mCtrl = (v - 0x2020202020202020L) & ~v;
      final long combined = (mQuote | mSlash | mCtrl) & 0x8080808080808080L;
      if (combined != 0) {
        final int idx = Long.numberOfTrailingZeros(combined) >>> 3;
        p += idx;
        if (buf[p] == '"') {
          r.pos = p + 1;
          final int len = p - start;
          return internKey
              ? internAsciiKey(buf, start, len)
              : new String(buf, start, len, StandardCharsets.ISO_8859_1);
        }
        break;
      }
      p += 8;
    }

    boolean ascii = true;
    while (p < end) {
      final byte b = buf[p];
      if (b == '"') {
        r.pos = p + 1;
        final int len = p - start;
        if (ascii) {
          return internKey
              ? internAsciiKey(buf, start, len)
              : new String(buf, start, len, StandardCharsets.ISO_8859_1);
        }
        return new String(buf, start, len, StandardCharsets.UTF_8);
      }
      if (b == '\\') {
        final String s = readDoubleQuotedEscaped(r, start, p, strictJson);
        if (internKey) s.hashCode();
        return s;
      }
      if (b >= 0) {
        if (strictJson ? (b < 0x20) : (b == '\n' || b == '\r')) {
          r.pos = p;
          throw r.error("control character or line break not allowed in double quotes");
        }
        p++;
      } else {
        ascii = false;
        p = validateAndAdvanceUtf8(r, buf, p, end);
      }
    }
    r.pos = p;
    throw r.error("unterminated double quote");
  }

  public static int validateAndAdvanceUtf8(
      final ByteReader r, final byte[] buf, final int p, final int end) {
    final byte b = buf[p];
    if ((b & 0xE0) == 0xC0) {
      if (p + 1 >= end || (buf[p + 1] & 0xC0) != 0x80 || (b & 0x1E) == 0) {
        r.pos = p;
        throw r.error("malformed UTF-8");
      }
      return p + 2;
    }
    if ((b & 0xF0) == 0xE0) {
      if (p + 2 >= end || (buf[p + 1] & 0xC0) != 0x80 || (buf[p + 2] & 0xC0) != 0x80) {
        r.pos = p;
        throw r.error("malformed UTF-8");
      }
      final int cp = ((b & 0x0F) << 12) | ((buf[p + 1] & 0x3F) << 6) | (buf[p + 2] & 0x3F);
      if (cp < 0x800 || (cp >= 0xD800 && cp <= 0xDFFF)) {
        r.pos = p;
        throw r.error("malformed UTF-8");
      }
      return p + 3;
    }
    if ((b & 0xF8) == 0xF0) {
      if (p + 3 >= end
          || (buf[p + 1] & 0xC0) != 0x80
          || (buf[p + 2] & 0xC0) != 0x80
          || (buf[p + 3] & 0xC0) != 0x80) {
        r.pos = p;
        throw r.error("malformed UTF-8");
      }
      final int cp =
          ((b & 0x07) << 18)
              | ((buf[p + 1] & 0x3F) << 12)
              | ((buf[p + 2] & 0x3F) << 6)
              | (buf[p + 3] & 0x3F);
      if (cp < 0x10000 || cp > 0x10FFFF) {
        r.pos = p;
        throw r.error("malformed UTF-8");
      }
      return p + 4;
    }
    r.pos = p;
    throw r.error("invalid UTF-8 byte");
  }

  public static String readDoubleQuotedEscaped(
      final ByteReader r, final int start, final int escapePos, final boolean strictJson) {
    final StringBuilder sb = r.sb;
    sb.setLength(0);
    r.pos = start;
    while (r.pos < escapePos) {
      r.appendUtf8(r.next());
    }

    final byte[] buf = r.buf;
    final int end = r.end;

    while (r.pos < end) {
      final byte b = buf[r.pos++];
      if (b == '"') {
        return sb.toString();
      }
      if (b == '\\') {
        if (r.pos >= end) throw r.error("incomplete escape");
        final byte esc = buf[r.pos++];
        switch (esc) {
          case '"':
            sb.append('"');
            break;
          case '\\':
            sb.append('\\');
            break;
          case '/':
            sb.append('/');
            break;
          case 'b':
            sb.append('\b');
            break;
          case 'f':
            sb.append('\f');
            break;
          case 'n':
            sb.append('\n');
            break;
          case 'r':
            sb.append('\r');
            break;
          case 't':
            sb.append('\t');
            break;
          case 'u':
            sb.append((char) readHexEscape(r, 4));
            break;
          case '0':
            if (strictJson) throw r.error("invalid \\0 escape in JSON");
            sb.append('\0');
            break;
          case 'e':
            if (strictJson) throw r.error("invalid \\e escape in JSON");
            sb.append('\u001B');
            break;
          case 'x':
            if (strictJson) throw r.error("invalid \\x escape in JSON");
            sb.append((char) readHexEscape(r, 2));
            break;
          case 'U':
            if (strictJson) throw r.error("invalid \\U escape in JSON");
            sb.appendCodePoint(readHexEscape(r, 8));
            break;
          default:
            throw r.error("unknown escape sequence: \\" + (char) esc);
        }
      } else if (b >= 0) {
        if (strictJson ? (b < 0x20) : (b == '\n' || b == '\r')) {
          throw r.error("control character or line break not allowed in double quotes");
        }
        sb.append((char) b);
      } else {
        r.appendUtf8(b);
      }
    }
    throw r.error("unterminated double quote");
  }

  public static int readHexEscape(final ByteReader r, final int digits) {
    if (r.pos + digits > r.end) {
      throw r.error("incomplete hexadecimal escape");
    }
    final byte[] buf = r.buf;
    int p = r.pos;
    int value = 0;
    for (int i = 0; i < digits; i++) {
      final byte b = buf[p++];
      final int d = (b >= 0) ? NumberParser.HEX_DIGITS[b] : -1;
      if (d < 0) {
        r.pos = p;
        throw r.error("invalid hexadecimal character in escape: " + (char) b);
      }
      value = (value << 4) | d;
    }
    r.pos = p;
    return value;
  }
}
