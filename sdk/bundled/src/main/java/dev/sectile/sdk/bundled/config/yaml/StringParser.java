package dev.sectile.sdk.bundled.config.yaml;

import dev.sectile.sdk.bundled.config.ByteReader;
import dev.sectile.sdk.bundled.config.StringDecodeUtil;
import java.nio.charset.StandardCharsets;

public final class StringParser {

  private StringParser() {}

  public static String readKey(final YamlReader r, final boolean inFlow) {
    final int end = r.end;
    int p = r.pos;
    if (p >= end) throw r.error("expected a key");

    final byte[] buf = r.buf;
    final byte first = buf[p];
    if (first == '\t') throw r.error("tabs are not allowed");
    if (first == ':') throw r.error("empty key is not valid");

    if (first == '"') {
      final String key = StringDecodeUtil.readDoubleQuoted(r, true, false);
      r.skipInlineSpaces();
      if (r.pos >= end || buf[r.pos++] != ':') {
        throw r.error("expected ':' after key '" + key + "'");
      }
      return key;
    }
    if (first == '\'') {
      final String key = readSingleQuoted(r, true);
      r.skipInlineSpaces();
      if (r.pos >= end || buf[r.pos++] != ':') {
        throw r.error("expected ':' after key '" + key + "'");
      }
      return key;
    }

    return readUnquotedKey(r, inFlow);
  }

  private static String readUnquotedKey(final YamlReader r, final boolean inFlow) {
    final byte[] buf = r.buf;
    final int end = r.end;
    final int start = r.pos;
    int p = start;

    // SWAR fast-path for common 1..7 character ASCII block keys (e.g. "name: ", "id:\n")
    if (!inFlow && p + 8 <= end) {
      final long v = ByteReader.getLongLE(buf, p);
      if ((v & 0x8080808080808080L) == 0) {
        final long xColon = v ^ 0x3A3A3A3A3A3A3A3AL; // ':'
        final long mColon = (xColon - 0x0101010101010101L) & ~xColon;
        final long mCtrl = (v - 0x2020202020202020L) & ~v; // < 0x20 (\n, \r, \t)
        final long combined = (mColon | mCtrl) & 0x8080808080808080L;
        if (combined != 0) {
          final int idx = Long.numberOfTrailingZeros(combined) >>> 3;
          final int colonPos = p + idx;
          if (buf[colonPos] == ':' && isKeyColonSeparator(buf, colonPos, end, false)) {
            int keyEnd = colonPos;
            while (keyEnd > start && buf[keyEnd - 1] == ' ') {
              keyEnd--;
            }
            final int len = keyEnd - start;
            if (len > 0) {
              r.pos = colonPos + 1;
              return StringDecodeUtil.internAsciiKey(buf, start, len);
            }
          }
        }
      }
    }

    boolean ascii = true;
    while (p < end) {
      final byte b = buf[p];
      if (b == ':' && isKeyColonSeparator(buf, p, end, inFlow)) {
        break;
      }
      if (b == '\n' || b == '\r' || b == '\t' || (inFlow && (b == ',' || b == '}' || b == ']'))) {
        r.pos = p;
        throw r.error("invalid key or missing ':'");
      }
      if (b < 0) {
        ascii = false;
      }
      p++;
    }

    if (p >= end) {
      r.pos = p;
      throw r.error("invalid key or missing ':'");
    }

    final int nextPos = p + 1; // Consume ':'
    int keyEnd = p;
    while (keyEnd > start && buf[keyEnd - 1] == ' ') {
      keyEnd--;
    }

    final int len = keyEnd - start;
    if (len == 0) {
      r.pos = nextPos;
      throw r.error("empty key");
    }

    r.pos = nextPos;
    if (ascii) {
      return StringDecodeUtil.internAsciiKey(buf, start, len);
    }
    final String s = new String(buf, start, len, StandardCharsets.UTF_8);
    s.hashCode();
    return s;
  }

  private static boolean isKeyColonSeparator(
      final byte[] buf, final int pos, final int end, final boolean inFlow) {
    final int nextIdx = pos + 1;
    if (nextIdx >= end) return true;
    final byte next = buf[nextIdx];
    if (next == ' ' || next == '\n' || next == '\r') return true;
    return inFlow
        && (next == ','
            || next == '}'
            || next == ']'
            || next == '"'
            || next == '\''
            || next == '{'
            || next == '[');
  }

  public static String readSingleQuoted(final YamlReader r, final boolean internKey) {
    final byte[] buf = r.buf;
    final int end = r.end;
    final int start = ++r.pos; // Consume initial '\''
    int p = start;
    boolean ascii = true;

    while (p < end) {
      final byte b = buf[p];
      if (b == '\'') {
        if (p + 1 < end && buf[p + 1] == '\'') {
          final String s = readSingleQuotedSlow(r, start, p);
          if (internKey) s.hashCode();
          return s;
        }
        r.pos = p + 1;
        final int len = p - start;
        if (ascii) {
          return internKey
              ? StringDecodeUtil.internAsciiKey(buf, start, len)
              : new String(buf, start, len, StandardCharsets.ISO_8859_1);
        }
        final String s = new String(buf, start, len, StandardCharsets.UTF_8);
        if (internKey) s.hashCode();
        return s;
      }
      if (b == '\n' || b == '\r') {
        r.pos = p;
        throw r.error("unterminated single quote on the same line");
      }
      if (b < 0) {
        ascii = false;
      }
      p++;
    }
    r.pos = p;
    throw r.error("unterminated single quote");
  }

  private static String readSingleQuotedSlow(
      final YamlReader r, final int start, final int firstQuotePos) {
    final StringBuilder sb = r.sb;
    sb.setLength(0);
    r.pos = start;
    while (r.pos < firstQuotePos) {
      r.appendUtf8(r.next());
    }

    final byte[] buf = r.buf;
    final int end = r.end;
    while (r.pos < end) {
      final byte b = buf[r.pos++];
      if (b == '\'') {
        if (r.pos < end && buf[r.pos] == '\'') {
          r.pos++;
          sb.append('\'');
          continue;
        }
        return sb.toString();
      }
      if (b == '\n' || b == '\r') {
        throw r.error("unterminated single quote on the same line");
      }
      r.appendUtf8(b);
    }
    throw r.error("unterminated single quote");
  }

  public static String readMultilineBlock(final YamlReader r, final int parentIndent) {
    final byte[] buf = r.buf;
    final int end = r.end;
    final boolean folded = (buf[r.pos++] == '>');
    int chomping = 0;

    if (r.pos < end) {
      final byte c = buf[r.pos];
      if (c == '-') {
        chomping = -1;
        r.pos++;
      } else if (c == '+') {
        chomping = 1;
        r.pos++;
      }
    }

    r.skipInlineSpaces();
    if (r.pos < end && buf[r.pos] == '#') {
      r.pos = ByteReader.indexOfNewline(buf, r.pos + 1, end);
    }
    if (r.pos < end && !r.isLineEnd(buf[r.pos])) {
      throw r.error("unexpected character after the multi-line block indicator");
    }

    final StringBuilder sb = r.sb;
    sb.setLength(0);
    int blockIndent = -1;
    int pendingNewlines = 0;
    boolean hasContent = false;
    boolean prevSpaced = false; // previous content line was "more indented" (folded style)

    while (r.pos < end) {
      final int savedPos = r.pos;
      final int savedLine = r.lineNumber;

      if (buf[r.pos] == '\r') r.pos++;
      if (r.pos >= end || buf[r.pos] != '\n') break;
      r.pos++;
      r.lineNumber++;

      int p = r.pos;
      while (p + 8 <= end && ByteReader.getLongLE(buf, p) == 0x2020202020202020L) {
        p += 8;
      }
      while (p < end && buf[p] == ' ') {
        p++;
      }
      final int lineIndent = p - r.pos;
      r.pos = p;

      if (p >= end || r.isLineEnd(buf[p])) {
        pendingNewlines++;
        continue;
      }

      if (blockIndent == -1) {
        if (lineIndent <= parentIndent) {
          r.pos = savedPos;
          r.lineNumber = savedLine;
          break;
        }
        blockIndent = lineIndent;
      } else if (lineIndent < blockIndent) {
        r.pos = savedPos;
        r.lineNumber = savedLine;
        break;
      }

      // In folded style, "spaced" lines (more indented, or starting with a tab) are never folded:
      // the line breaks around them are preserved.
      final boolean spaced = lineIndent > blockIndent || buf[p] == '\t';

      if (hasContent) {
        if (!folded || spaced || prevSpaced) {
          sb.repeat("\n", pendingNewlines + 1);
        } else if (pendingNewlines == 0) {
          sb.append(' ');
        } else {
          sb.repeat("\n", pendingNewlines);
        }
      } else {
        sb.repeat("\n", pendingNewlines);
      }
      pendingNewlines = 0;
      hasContent = true;
      prevSpaced = spaced;

      sb.repeat(" ", Math.max(0, lineIndent - blockIndent));

      while (r.pos < end && !r.isLineEnd(buf[r.pos])) {
        r.appendUtf8(buf[r.pos++]);
      }
    }

    if (!hasContent) {
      if (chomping == 1) {
        sb.repeat("\n", Math.max(0, pendingNewlines));
        return sb.toString();
      }
      return "";
    }

    if (chomping == 0) {
      sb.append('\n');
    } else if (chomping == 1) {
      sb.repeat("\n", Math.max(0, pendingNewlines + 1));
    }
    return sb.toString();
  }

  public static String decodeSlice(final YamlReader r, final int start, final int end) {
    return StringDecodeUtil.decodeSlice(r.buf, start, end);
  }
}
