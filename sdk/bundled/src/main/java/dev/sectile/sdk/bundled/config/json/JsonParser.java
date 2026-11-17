package dev.sectile.sdk.bundled.config.json;

import dev.sectile.kernel.config.ConfigSection;
import dev.sectile.kernel.config.section.MapConfigSection;
import dev.sectile.sdk.bundled.config.ByteReader;
import dev.sectile.sdk.bundled.config.NumberParser;
import dev.sectile.sdk.bundled.config.StringDecodeUtil;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class JsonParser {

  private static final int MAX_DEPTH = 512;

  private final ByteReader reader = new ByteReader();

  public ConfigSection parseObject(final byte[] buf) {
    return parseObject(buf, 0, buf.length);
  }

  public ConfigSection parseObject(final byte[] buf, final int offset, final int length) {
    final ByteReader r = this.reader;
    r.reset(buf, offset, length);
    r.skipJsonWhitespace();
    if (r.pos >= r.end || r.buf[r.pos] != '{') {
      throw r.error("expected '{' at the beginning of JSON");
    }
    final ConfigSection result = readObject(r, new LinkedHashMap<>(), 0);
    r.skipJsonWhitespace();
    if (r.pos < r.end) {
      throw r.error("unexpected content after JSON closure");
    }
    return result;
  }

  public Object parse(final byte[] buf) {
    final ByteReader r = this.reader;
    r.reset(buf, 0, buf.length);
    r.skipJsonWhitespace();
    if (r.pos >= r.end) {
      throw r.error("empty JSON");
    }
    final Object value = readValue(r, 0);
    r.skipJsonWhitespace();
    if (r.pos < r.end) {
      throw r.error("unexpected content at the end of JSON");
    }
    return value;
  }

  private static Object readValue(final ByteReader r, final int depth) {
    if (r.pos >= r.end) {
      throw r.error("unexpected end of input");
    }
    final byte b = r.buf[r.pos];
    return switch (b) {
      case '"' -> StringDecodeUtil.readDoubleQuoted(r, false, true);
      case '{' -> readObject(r, new LinkedHashMap<>(8), depth + 1);
      case '[' -> readArray(r, depth + 1);
      case 't' -> readTrue(r);
      case 'f' -> readFalse(r);
      case 'n' -> readNull(r);
      case '-', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9' -> NumberParser.readJsonNumber(r);
      default -> throw r.error("unexpected JSON character: '" + (char) b + "'");
    };
  }

  private static ConfigSection readObject(
      final ByteReader r, final Map<String, Object> map, final int depth) {
    if (depth > MAX_DEPTH) {
      throw r.error("maximum nesting depth exceeded (" + MAX_DEPTH + ")");
    }
    final byte[] buf = r.buf;
    final int end = r.end;

    r.pos++; // Consume '{'
    if (r.pos < end && buf[r.pos] == '}') {
      r.pos++;
      return new MapConfigSection(map);
    }
    r.skipJsonWhitespace();
    if (r.pos < end && buf[r.pos] == '}') {
      r.pos++;
      return new MapConfigSection(map);
    }

    for (; ; ) {
      if (r.pos >= end || buf[r.pos] != '"') {
        throw r.error("expected a key in double quotes '\"'");
      }
      final String key = StringDecodeUtil.readJsonKeyAndColon(r);
      final Object value = readValue(r, depth);
      map.put(key, value);

      int p = r.pos;
      if (p < end && buf[p] <= ' ') {
        r.skipJsonWhitespace();
        p = r.pos;
      }
      if (p >= end) {
        throw r.error("unclosed object '{'");
      }
      final byte next = buf[p++];
      r.pos = p;
      if (next == '}') {
        return new MapConfigSection(map);
      }
      if (next != ',') {
        throw r.error("expected ',' or '}' in object");
      }
      if (p < end && buf[p] <= ' ') {
        r.skipJsonWhitespace();
      }
      // Support trailing comma in JSON objects (e.g. {"a": 1, })
      if (r.pos < end && buf[r.pos] == '}') {
        r.pos++;
        return new MapConfigSection(map);
      }
    }
  }

  private static List<Object> readArray(final ByteReader r, final int depth) {
    if (depth > MAX_DEPTH) {
      throw r.error("maximum nesting depth exceeded (" + MAX_DEPTH + ")");
    }
    final byte[] buf = r.buf;
    final int end = r.end;

    r.pos++; // Consume '['
    if (r.pos < end && buf[r.pos] == ']') {
      r.pos++;
      return new ArrayList<>(0);
    }
    r.skipJsonWhitespace();
    if (r.pos < end && buf[r.pos] == ']') {
      r.pos++;
      return new ArrayList<>(0);
    }

    final List<Object> list = new ArrayList<>(8);
    for (; ; ) {
      list.add(readValue(r, depth));

      int p = r.pos;
      if (p < end && buf[p] <= ' ') {
        r.skipJsonWhitespace();
        p = r.pos;
      }
      if (p >= end) {
        throw r.error("unclosed array '['");
      }
      final byte next = buf[p++];
      r.pos = p;
      if (next == ']') {
        return list;
      }
      if (next != ',') {
        throw r.error("expected ',' or ']' in array");
      }
      if (p < end && buf[p] <= ' ') {
        r.skipJsonWhitespace();
      }
      // Support trailing comma in JSON arrays (e.g. [1, 2, ])
      if (r.pos < end && buf[r.pos] == ']') {
        r.pos++;
        return list;
      }
    }
  }

  private static Boolean readTrue(final ByteReader r) {
    final int p = r.pos;
    if (p + 4 <= r.end && ByteReader.getIntLE(r.buf, p) == ByteReader.INT_TRUE) {
      r.pos = p + 4;
      return Boolean.TRUE;
    }
    throw r.error("expected 'true'");
  }

  private static Boolean readFalse(final ByteReader r) {
    final int p = r.pos;
    final byte[] buf = r.buf;
    if (p + 5 <= r.end && buf[p + 4] == 'e' && ByteReader.getIntLE(buf, p) == ByteReader.INT_FALS) {
      r.pos = p + 5;
      return Boolean.FALSE;
    }
    throw r.error("expected 'false'");
  }

  private static Object readNull(final ByteReader r) {
    final int p = r.pos;
    if (p + 4 <= r.end && ByteReader.getIntLE(r.buf, p) == ByteReader.INT_NULL) {
      r.pos = p + 4;
      return null;
    }
    throw r.error("expected 'null'");
  }
}
