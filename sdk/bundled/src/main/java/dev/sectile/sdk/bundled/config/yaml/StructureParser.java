package dev.sectile.sdk.bundled.config.yaml;

import dev.sectile.kernel.config.ConfigSection;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class StructureParser {

  private static final int MAX_DEPTH = 32;
  private static final int MAX_FLOW_DEPTH = 512;

  private final int[] indents = new int[MAX_DEPTH];
  private final Object[] containers = new Object[MAX_DEPTH];
  private final boolean[] isListStack = new boolean[MAX_DEPTH];
  private int depth = 0;

  private String pendingKey = null;
  private boolean pendingListElement = false;
  private int pendingIndent = -1;

  public void reset(final ConfigSection root) {
    this.depth = 0;
    this.indents[0] = 0;
    this.containers[0] = root;
    this.isListStack[0] = false;
    this.pendingKey = null;
    this.pendingListElement = false;
    this.pendingIndent = -1;
  }

  public boolean hasPending() {
    return pendingKey != null || pendingListElement;
  }

  @SuppressWarnings("unchecked")
  public void flushPending(final YamlReader r) {
    if (pendingKey != null) {
      putInCurrentMap(r, pendingKey, null);
      pendingKey = null;
    } else if (pendingListElement) {
      ((List<Object>) containers[depth]).add(null);
      pendingListElement = false;
    }
  }

  @SuppressWarnings("unchecked")
  public void resolveIndentation(final YamlReader r, final int indent, final boolean isListItem) {
    if (pendingKey != null) {
      final boolean validNested = isListItem ? (indent >= pendingIndent) : (indent > pendingIndent);
      if (validNested) {
        final Object child = isListItem ? new ArrayList<>(8) : new LinkedHashMap<String, Object>(8);
        putInCurrentMap(r, pendingKey, child);
        pushContainer(r, indent, child, isListItem);
        pendingKey = null;
        return;
      } else {
        putInCurrentMap(r, pendingKey, null);
        pendingKey = null;
      }
    } else if (pendingListElement) {
      if (indent > pendingIndent) {
        final Object child = isListItem ? new ArrayList<>(8) : new LinkedHashMap<String, Object>(8);
        ((List<Object>) containers[depth]).add(child);
        pushContainer(r, indent, child, isListItem);
        pendingListElement = false;
        return;
      } else {
        ((List<Object>) containers[depth]).add(null);
        pendingListElement = false;
      }
    }

    int d = this.depth;
    while (d > 0
        && (indent < indents[d] || (!isListItem && indent == indents[d] && isListStack[d]))) {
      d--;
    }
    this.depth = d;

    if (indent != indents[d]) {
      throw r.error(
          "invalid indentation (expected " + indents[d] + " spaces, found " + indent + ")");
    }
  }

  public void parseBlockMapEntry(final YamlReader r, final int indent) {
    final String key = StringParser.readKey(r, false);
    r.skipInlineSpaces();

    if (r.pos >= r.end) {
      this.pendingKey = key;
      this.pendingIndent = indent;
      return;
    }
    final byte next = r.buf[r.pos];
    if (next == '\n' || next == '\r' || next == '#') {
      this.pendingKey = key;
      this.pendingIndent = indent;
    } else {
      final Object value = ScalarParser.readValue(r, false, indent);
      putInCurrentMap(r, key, value);
    }
  }

  @SuppressWarnings("unchecked")
  public void parseBlockListItem(final YamlReader r, final int dashIndent) {
    final int d = this.depth;
    if (!isListStack[d]) {
      throw r.error("unexpected '-' list item in this block");
    }
    final List<Object> currentList = (List<Object>) containers[d];

    final byte[] buf = r.buf;
    final int end = r.end;
    int p = r.pos + 1; // Consume '-'
    while (p < end && buf[p] == ' ') {
      p++;
    }
    final int spacesAfterDash = p - (r.pos + 1);
    r.pos = p;

    if (p >= end || buf[p] == '\n' || buf[p] == '\r' || buf[p] == '#') {
      this.pendingListElement = true;
      this.pendingIndent = dashIndent;
      return;
    }

    final int contentIndent = dashIndent + 1 + spacesAfterDash;

    if (isBlockListItem(r)) {
      final List<Object> subList = new ArrayList<>(8);
      currentList.add(subList);
      pushContainer(r, contentIndent, subList, true);
      parseBlockListItem(r, contentIndent);
      return;
    }

    if (looksLikeKeyValuePair(buf, p, end)) {
      final Map<String, Object> itemMap = new LinkedHashMap<>(8);
      currentList.add(itemMap);
      pushContainer(r, contentIndent, itemMap, false);
      parseBlockMapEntry(r, contentIndent);
    } else {
      currentList.add(ScalarParser.readValue(r, false, dashIndent));
    }
  }

  private static void enterFlow(final YamlReader r) {
    if (++r.flowDepth > MAX_FLOW_DEPTH) {
      throw r.error("maximum flow nesting depth exceeded (" + MAX_FLOW_DEPTH + ")");
    }
  }

  public static ConfigSection readFlowMap(final YamlReader r, final ConfigSection map) {
    enterFlow(r);
    final ConfigSection result = readFlowMapBody(r, map);
    r.flowDepth--;
    return result;
  }

  private static ConfigSection readFlowMapBody(final YamlReader r, final ConfigSection map) {
    final byte[] buf = r.buf;
    final int end = r.end;
    r.pos++; // Consume '{'

    if (r.pos < end && buf[r.pos] == '}') {
      r.pos++;
      return map;
    }

    while (r.pos < end) {
      r.skipFlowWhitespace();
      if (r.pos >= end) break;

      if (buf[r.pos] == '}') {
        r.pos++;
        return map;
      }

      final String key = StringParser.readKey(r, true);
      final Object value = ScalarParser.readValue(r, true, 0);
      map.set(key, value);

      r.skipFlowWhitespace();
      if (r.pos >= end) break;

      final byte next = buf[r.pos];
      if (next == ',') {
        r.pos++;
      } else if (next == '}') {
        r.pos++;
        return map;
      } else {
        throw r.error("expected ',' or '}' in JSON/Flow object");
      }
    }
    throw r.error("unclosed object '{'");
  }

  public static List<Object> readFlowList(final YamlReader r) {
    enterFlow(r);
    final List<Object> result = readFlowListBody(r);
    r.flowDepth--;
    return result;
  }

  private static List<Object> readFlowListBody(final YamlReader r) {
    final byte[] buf = r.buf;
    final int end = r.end;
    r.pos++; // Consume '['

    if (r.pos < end && buf[r.pos] == ']') {
      r.pos++;
      return new ArrayList<>(0);
    }
    r.skipFlowWhitespace();
    if (r.pos < end && buf[r.pos] == ']') {
      r.pos++;
      return new ArrayList<>(0);
    }

    final List<Object> list = new ArrayList<>(8);
    while (r.pos < end) {
      if (buf[r.pos] == ']') {
        r.pos++;
        return list;
      }

      list.add(ScalarParser.readValue(r, true, 0));

      r.skipFlowWhitespace();
      if (r.pos >= end) break;

      final byte next = buf[r.pos];
      if (next == ',') {
        r.pos++;
        r.skipFlowWhitespace();
      } else if (next == ']') {
        r.pos++;
        return list;
      } else {
        throw r.error("expected ',' or ']' in list");
      }
    }
    throw r.error("unclosed list '['");
  }

  public static boolean isBlockListItem(final YamlReader r) {
    final int p = r.pos;
    final byte[] buf = r.buf;
    if (buf[p] != '-') return false;
    if (p + 1 >= r.end) return true;
    final byte next = buf[p + 1];
    return next == ' ' || next == '\n' || next == '\r';
  }

  private static boolean looksLikeKeyValuePair(final byte[] buf, int i, final int end) {
    final byte first = buf[i];
    if (first == '[' || first == '{') return false;

    // Must follow the same rules as the real readers: backslash escapes in double quotes,
    // '' escapes in single quotes.
    if (first == '"' || first == '\'') {
      i++;
      while (i < end) {
        final byte b = buf[i];
        if (b == '\n' || b == '\r') return false;
        if (first == '"' && b == '\\') {
          i += 2;
          continue;
        }
        if (b == first) {
          if (first == '\'' && i + 1 < end && buf[i + 1] == '\'') {
            i += 2;
            continue;
          }
          break;
        }
        i++;
      }
      if (i >= end) return false;
      i++; // closing quote
      while (i < end && buf[i] == ' ') {
        i++;
      }
      return i < end && buf[i] == ':';
    }

    while (i < end) {
      final byte b = buf[i];
      if (b == '\n' || b == '\r') break;
      // '#' only starts a comment when preceded by a space (same rule as ScalarParser)
      if (b == '#' && i > 0 && buf[i - 1] == ' ') break;
      if (b == ':') {
        if (i + 1 >= end) return true;
        final byte next = buf[i + 1];
        return next == ' ' || next == '\n' || next == '\r';
      }
      i++;
    }
    return false;
  }

  private void pushContainer(
      final YamlReader r, final int indent, final Object container, final boolean isList) {
    final int d = ++this.depth;
    if (d >= MAX_DEPTH) {
      throw r.error("maximum depth exceeded (" + MAX_DEPTH + ")");
    }
    indents[d] = indent;
    containers[d] = container;
    isListStack[d] = isList;
  }

  @SuppressWarnings("unchecked")
  private void putInCurrentMap(final YamlReader r, final String key, final Object value) {
    final int d = this.depth;
    if (isListStack[d]) {
      throw r.error("cannot insert a key inside a list");
    }
    final Object container = containers[d];
    if (container instanceof ConfigSection) {
      ((ConfigSection) container).set(key, value);
      return;
    }
    ((Map<String, Object>) container).put(key, value);
  }
}
