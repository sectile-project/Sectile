package dev.sectile.boostrap.terminal;

import dev.sectile.boostrap.terminal.type.Terminal;

/**
 * Decodes raw characters and ANSI escape sequences from a {@link Terminal} into {@link Key} events.
 */
final class KeyReader {
  private final Terminal terminal;

  /** The character of the last {@link Key#PRINTABLE} returned by {@link #readKey()}. */
  char lastChar;

  KeyReader(Terminal terminal) {
    this.terminal = terminal;
  }

  Key readKey() {
    int ch = terminal.readChar();
    return switch (ch) {
      case -1 -> Key.EOF;
      case '\r', '\n' -> Key.ENTER;
      case '\t' -> Key.TAB;
      case 1 -> Key.HOME;
      case 3 -> Key.CTRL_C;
      case 4 -> Key.CTRL_D;
      case 5 -> Key.END;
      case 8, 127 -> Key.BACKSPACE;
      case 27 -> terminal.hasBufferedInput() ? readEscapeSequence() : Key.ESCAPE;
      default -> {
        if (ch < 32) yield Key.UNKNOWN;
        lastChar = (char) ch;
        yield Key.PRINTABLE;
      }
    };
  }

  private Key readEscapeSequence() {
    return switch (terminal.readChar()) {
      case '[' -> readCsi();
      case 'O' -> {
        Key key = cursorKey(terminal.readChar());
        yield key != null ? key : Key.UNKNOWN;
      }
      default -> Key.UNKNOWN;
    };
  }

  /**
   * Reads a complete CSI sequence ({@code ESC [ <params> <final>}), so modified keys such as {@code
   * ESC[3;5~} are fully consumed instead of leaking their tail into the input buffer.
   */
  private Key readCsi() {
    StringBuilder params = new StringBuilder();
    int c = terminal.readChar();
    while (c >= 0x20 && c <= 0x3F) { // parameter and intermediate bytes
      params.append((char) c);
      c = terminal.readChar();
    }
    Key key = cursorKey(c);
    if (key != null) return key;
    if (c == 'Z') return Key.SHIFT_TAB;
    if (c == '~') {
      int semicolon = params.indexOf(";");
      String first = semicolon < 0 ? params.toString() : params.substring(0, semicolon);
      return switch (first) {
        case "1", "7" -> Key.HOME;
        case "3" -> Key.DELETE;
        case "4", "8" -> Key.END;
        default -> Key.UNKNOWN;
      };
    }
    return Key.UNKNOWN;
  }

  private static Key cursorKey(int c) {
    return switch (c) {
      case 'A' -> Key.UP;
      case 'B' -> Key.DOWN;
      case 'C' -> Key.RIGHT;
      case 'D' -> Key.LEFT;
      case 'H' -> Key.HOME;
      case 'F' -> Key.END;
      default -> null;
    };
  }
}
