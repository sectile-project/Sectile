package dev.sectile.sdk.bundled.config.yaml;

import dev.sectile.sdk.bundled.config.ByteReader;

public final class YamlReader extends ByteReader {

  /** Current nesting depth of flow collections ({@code {}} / {@code []}). */
  public int flowDepth;

  @Override
  public void reset(final byte[] buf, final int offset, final int length) {
    super.reset(buf, offset, length);
    this.flowDepth = 0;
  }

  public YamlReader() {
    super();
  }

  public YamlReader(final byte[] buf) {
    super(buf);
  }

  public void skipInlineSpaces() {
    final int end = this.end;
    int p = this.pos;
    if (p >= end) return;

    final byte[] buf = this.buf;
    final byte first = buf[p];
    if (first != ' ') {
      if (first == '\t') {
        throw error("tabs are not allowed");
      }
      return;
    }

    while (p + 8 <= end && getLongLE(buf, p) == 0x2020202020202020L) {
      p += 8;
    }

    while (p < end) {
      final byte b = buf[p];
      if (b == ' ') {
        p++;
      } else if (b == '\t') {
        this.pos = p;
        throw error("tabs are not allowed");
      } else {
        break;
      }
    }
    this.pos = p;
  }

  public void skipFlowWhitespace() {
    final int end = this.end;
    int p = this.pos;
    if (p >= end) return;

    final byte[] buf = this.buf;
    final byte first = buf[p];
    if (first > ' ' && first != '#') {
      return;
    }

    int lines = this.lineNumber;
    while (p < end) {
      final byte b = buf[p];
      if (b == ' ' || b == '\r') {
        p++;
      } else if (b == '\n') {
        p++;
        lines++;
      } else if (b == '#') {
        p = indexOfNewline(buf, p + 1, end);
      } else if (b == '\t') {
        this.pos = p;
        this.lineNumber = lines;
        throw error("tabs are not allowed");
      } else {
        break;
      }
    }
    this.pos = p;
    this.lineNumber = lines;
  }

  public void skipToNextLine() {
    final int end = this.end;
    int p = indexOfNewline(this.buf, this.pos, end);
    if (p < end) {
      p++;
      this.lineNumber++;
    }
    this.pos = p;
  }

  @Override
  public IllegalArgumentException error(final String msg) {
    return new IllegalArgumentException("Invalid YAML (line " + lineNumber + "): " + msg);
  }
}
