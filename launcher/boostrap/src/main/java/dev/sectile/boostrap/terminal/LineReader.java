package dev.sectile.boostrap.terminal;

import dev.sectile.boostrap.terminal.completer.Completer;
import dev.sectile.boostrap.terminal.config.TerminalConfig;
import dev.sectile.boostrap.terminal.type.Terminal;

/**
 * Reads a single line from the terminal with a 2-state autocomplete UX:
 *
 * <ul>
 *   <li>Passive mode (while typing): shows inline ghost text and menu without stealing arrow keys
 *       or Enter.
 *   <li>Focused mode (after Tab or Down): arrow keys navigate the 2D grid, Enter accepts, Esc
 *       cancels.
 * </ul>
 *
 * This class only edits the line and dispatches keys; suggestions live in {@link Suggestions},
 * drawing in {@link Renderer}, key decoding in {@link KeyReader}. Not thread-safe.
 */
public final class LineReader {
  private final Terminal terminal;
  private final TerminalConfig config;
  private final KeyReader keys;
  private final Suggestions suggestions;
  private final Renderer renderer;
  private final Object ioLock = new Object();

  // State of the line being read
  private final StringBuilder buffer = new StringBuilder();
  private int cursor;
  private String result;
  private volatile boolean reading;

  public LineReader(Terminal terminal) {
    this(terminal, null, new TerminalConfig());
  }

  public LineReader(Terminal terminal, Completer completer) {
    this(terminal, completer, new TerminalConfig());
  }

  public LineReader(Terminal terminal, Completer completer, TerminalConfig config) {
    if (config.maxMenuRows < 1) {
      throw new IllegalArgumentException("maxMenuRows must be >= 1, was " + config.maxMenuRows);
    }
    this.terminal = terminal;
    this.config = config;
    this.keys = new KeyReader(terminal);
    this.suggestions = new Suggestions(completer, config);
    this.renderer = new Renderer(terminal, config.colors);
  }

  public String readLine() {
    return readLine(config.defaultPrompt);
  }

  /** Returns the line, or {@code null} on EOF (Ctrl+D on empty line) or Ctrl+C. */
  public String readLine(String prompt) {
    synchronized (ioLock) {
      buffer.setLength(0);
      cursor = 0;
      result = null;
      suggestions.menu.clear();
      renderer.begin(prompt);
    }

    terminal.enterRawMode();
    try {
      synchronized (ioLock) {
        reading = true;
        render();
      }
      while (true) {
        Key key = keys.readKey();
        synchronized (ioLock) {
          if (handle(key)) {
            reading = false;
            return result;
          }
          render();
        }
      }
    } finally {
      synchronized (ioLock) {
        reading = false;
      }
      terminal.exitRawMode();
    }
  }

  /**
   * Thread-safely prints {@code text} above the active prompt and redraws the input line and menu
   * below. When not actively reading a line, writes directly to the underlying terminal.
   */
  public void printAbove(CharSequence text) {
    if (text.isEmpty()) return;
    synchronized (ioLock) {
      if (!reading) {
        terminal.write(text.toString());
        terminal.flush();
        return;
      }
      renderer.printAbove(
          text,
          buffer,
          cursor,
          suggestions.ghost(buffer, cursor),
          suggestions.menu,
          terminal.width());
    }
  }

  /**
   * Applies {@code key} to the line. Returns true when the line is finished (see {@link #result}).
   */
  private boolean handle(Key key) {
    int width = terminal.width();
    CompletionMenu menu = suggestions.menu;

    switch (key) {
      case EOF -> {
        return finish(buffer.isEmpty() ? null : buffer.toString(), "");
      }
      case ENTER -> {
        if (menu.focused && menu.selectedMatch() != null) accept();
        else return finish(buffer.toString(), "\n");
      }
      case CTRL_C -> {
        return finish(null, "^C\n");
      }
      case CTRL_D -> {
        if (buffer.isEmpty()) return finish(null, "\n");
      }
      case ESCAPE -> menu.clear();

      case TAB -> {
        if (config.tabCompletion) {
          if (menu.focused) menu.next(width);
          else suggestions.open(buffer, cursor, width, false);
        }
      }
      case SHIFT_TAB -> {
        if (config.tabCompletion) {
          if (menu.focused) menu.previous(width);
          else suggestions.open(buffer, cursor, width, true);
        }
      }
      case UP -> {
        if (menu.focused) menu.moveUp(width);
        else if (config.tabCompletion) suggestions.open(buffer, cursor, width, true);
      }
      case DOWN -> {
        if (menu.focused) menu.moveDown(width);
        else if (config.tabCompletion) suggestions.open(buffer, cursor, width, false);
      }
      case LEFT -> {
        if (menu.focused) menu.previous(width);
        else moveCursor(cursor - 1);
      }
      case RIGHT -> {
        if (menu.focused) menu.next(width);
        else if (cursor < buffer.length()) moveCursor(cursor + 1);
        else if (config.acceptWithRightArrow && menu.selectedMatch() != null) accept();
      }
      case HOME -> moveCursor(0);
      case END -> moveCursor(buffer.length());

      case BACKSPACE -> {
        menu.focused = false;
        if (cursor > 0) {
          cursor--;
          buffer.deleteCharAt(cursor);
          suggestions.refresh(buffer, cursor);
        }
      }
      case DELETE -> {
        menu.focused = false;
        if (cursor < buffer.length()) {
          buffer.deleteCharAt(cursor);
          suggestions.refresh(buffer, cursor);
        }
      }
      case PRINTABLE -> {
        menu.focused = false;
        buffer.insert(cursor, keys.lastChar);
        cursor++;
        suggestions.refresh(buffer, cursor);
      }
      case UNKNOWN -> {}
    }
    return false;
  }

  /** Moves the cursor and refreshes the menu so it never refers to a stale word. */
  private void moveCursor(int position) {
    suggestions.menu.focused = false;
    cursor = Math.max(0, Math.min(position, buffer.length()));
    suggestions.refresh(buffer, cursor);
  }

  private void accept() {
    int newCursor = suggestions.accept(buffer, cursor);
    if (newCursor >= 0) cursor = newCursor;
  }

  private boolean finish(String line, String suffix) {
    renderer.finish(buffer, suffix);
    result = line;
    return true;
  }

  private void render() {
    renderer.render(
        buffer, cursor, suggestions.ghost(buffer, cursor), suggestions.menu, terminal.width());
  }
}
