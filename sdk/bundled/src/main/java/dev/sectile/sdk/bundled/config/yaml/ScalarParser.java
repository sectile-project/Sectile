package dev.sectile.sdk.bundled.config.yaml;

import dev.sectile.kernel.config.section.MapConfigSection;
import dev.sectile.sdk.bundled.config.ByteReader;
import dev.sectile.sdk.bundled.config.NumberParser;
import dev.sectile.sdk.bundled.config.StringDecodeUtil;
import java.util.LinkedHashMap;

public final class ScalarParser {

  private ScalarParser() {}

  public static Object readValue(final YamlReader r, final boolean inFlow, final int parentIndent) {
    if (inFlow) {
      r.skipFlowWhitespace();
    } else {
      r.skipInlineSpaces();
    }
    if (r.pos >= r.end) return null;

    final byte[] buf = r.buf;
    final byte first = buf[r.pos];
    if (!inFlow && (first == '\n' || first == '\r' || first == '#')) {
      return null;
    }
    if (!inFlow && (first == '|' || first == '>') && isMultilineHeader(buf, r.pos, r.end)) {
      return StringParser.readMultilineBlock(r, parentIndent);
    }
    if (first == '"') {
      final String val = StringDecodeUtil.readDoubleQuoted(r, false, false);
      if (!inFlow) consumeTrailingCommentOrSpace(r);
      return val;
    }
    if (first == '\'') {
      final String val = StringParser.readSingleQuoted(r, false);
      if (!inFlow) consumeTrailingCommentOrSpace(r);
      return val;
    }
    if (first == '{') {
      final Object map =
          StructureParser.readFlowMap(r, new MapConfigSection(new LinkedHashMap<>(8)));
      if (!inFlow) consumeTrailingCommentOrSpace(r);
      return map;
    }
    if (first == '[') {
      final Object list = StructureParser.readFlowList(r);
      if (!inFlow) consumeTrailingCommentOrSpace(r);
      return list;
    }

    final int start = r.pos;
    final int limit = r.end;
    int p = start;
    int sliceEnd = start;

    while (p < limit) {
      final byte b = buf[p];
      if (b == '\n' || b == '\r') break;
      if (inFlow && (b == ',' || b == ']' || b == '}')) break;
      if (b == '#' && p > start && buf[p - 1] == ' ') break;

      p++;
      if (b != ' ') {
        sliceEnd = p;
      }
    }
    r.pos = p;

    if (!inFlow) {
      consumeTrailingCommentOrSpace(r);
    }

    return parseSlice(buf, start, sliceEnd);
  }

  private static boolean isMultilineHeader(final byte[] buf, final int pos, final int end) {
    int i = pos + 1;
    if (i < end && (buf[i] == '-' || buf[i] == '+')) {
      i++;
    }
    if (i >= end) return true;
    final byte b = buf[i];
    return b == ' ' || b == '#' || b == '\n' || b == '\r';
  }

  private static Object parseSlice(final byte[] b, final int start, final int end) {
    final int len = end - start;
    if (len <= 0) return null;

    final byte first = b[start];

    switch (len) {
      case 1:
        if (first == '~') return null;
        if (first >= '0' && first <= '9') return first - '0';
        break;
      case 4:
        final int word4 = ByteReader.getIntLE(b, start);
        if (word4 == ByteReader.INT_NULL) return null;
        if (word4 == ByteReader.INT_TRUE) return Boolean.TRUE;
        break;
      case 5:
        if (b[start + 4] == 'e' && ByteReader.getIntLE(b, start) == ByteReader.INT_FALS) {
          return Boolean.FALSE;
        }
        break;
      default:
        break;
    }

    if ((first >= '0' && first <= '9') || first == '-' || first == '+') {
      final Number num = NumberParser.tryParseNumber(b, start, end);
      if (num != null) return num;
    }

    return StringDecodeUtil.decodeSlice(b, start, end);
  }

  /**
   * Consumes spaces and an optional trailing comment, then requires the end of the line. Anything
   * else after a complete value (e.g. {@code key: "abc" def}) is an error.
   */
  static void consumeTrailingCommentOrSpace(final YamlReader r) {
    r.skipInlineSpaces();
    if (r.pos < r.end && r.buf[r.pos] == '#') {
      // A comment needs whitespace (or the line start) before the '#'
      if (r.pos > 0 && r.buf[r.pos - 1] != ' ') {
        throw r.error("a comment must be separated from the value by a space");
      }
      r.pos = ByteReader.indexOfNewline(r.buf, r.pos + 1, r.end);
    }
    if (r.pos < r.end && !r.isLineEnd(r.buf[r.pos])) {
      throw r.error("unexpected content after the value");
    }
  }
}
