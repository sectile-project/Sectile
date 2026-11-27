package dev.sectile.sdk.bundled.config.yaml;

import dev.sectile.kernel.config.ConfigSection;
import dev.sectile.kernel.config.section.MapConfigSection;

public final class StrictYamlParser {

  private final StructureParser structures = new StructureParser();
  private final YamlReader reader = new YamlReader();

  public ConfigSection parse(final byte[] buf, final int offset, final int length) {
    final ConfigSection result = new MapConfigSection();
    final YamlReader r = this.reader;
    r.reset(buf, offset, length);
    structures.reset(result);

    while (r.pos < r.end) {
      parseSingleLine(r, result);
    }

    structures.flushPending(r);
    return result;
  }

  private void parseSingleLine(final YamlReader r, final ConfigSection root) {
    final byte[] buf = r.buf;
    final int end = r.end;
    int p = r.pos;

    // SWAR 8-space indentation fast path
    while (p + 8 <= end && YamlReader.getLongLE(buf, p) == 0x2020202020202020L) {
      p += 8;
    }

    while (p < end) {
      final byte b = buf[p];
      if (b == ' ') {
        p++;
      } else if (b == '\t') {
        r.pos = p;
        throw r.error("tabs are not allowed for indentation");
      } else {
        break;
      }
    }

    final int indent = p - r.pos;
    r.pos = p;

    if (p >= end) return;

    final byte first = buf[p];
    if (first == '\r' || first == '\n' || first == '#') {
      r.skipToNextLine();
      return;
    }

    if (indent == 0) {
      if (isDocumentMarker(buf, p, end, (byte) '-')) {
        r.skipToNextLine();
        return;
      }
      if (isDocumentMarker(buf, p, end, (byte) '.')) {
        r.pos = end;
        return;
      }
      if (first == '{' && !structures.hasPending()) {
        StructureParser.readFlowMap(r, root);
        ScalarParser.consumeTrailingCommentOrSpace(r);
        r.skipToNextLine();
        return;
      }
    }

    final boolean isListItem = StructureParser.isBlockListItem(r);
    structures.resolveIndentation(r, indent, isListItem);

    if (isListItem) {
      structures.parseBlockListItem(r, indent);
    } else {
      structures.parseBlockMapEntry(r, indent);
    }

    r.skipToNextLine();
  }

  private static boolean isDocumentMarker(
      final byte[] buf, final int p, final int end, final byte ch) {
    if (p + 2 >= end) return false;
    if (buf[p] == ch && buf[p + 1] == ch && buf[p + 2] == ch) {
      if (p + 3 >= end) return true;
      final byte next = buf[p + 3];
      return next == ' ' || next == '\n' || next == '\r';
    }
    return false;
  }
}
