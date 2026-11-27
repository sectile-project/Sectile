package dev.sectile.boostrap.terminal;

import dev.sectile.boostrap.terminal.config.Ansi;
import dev.sectile.boostrap.terminal.config.TerminalColors;
import dev.sectile.boostrap.terminal.type.Terminal;

/**
 * Draws the prompt line, ghost text and menu, and keeps track of where the cursor was left so the
 * next redraw can start from the right row (the input may wrap over several terminal rows).
 */
final class Renderer {
  private final Terminal terminal;
  private final String promptAnsi;
  private final String ghostAnsi;
  private final StringBuilder out = new StringBuilder();

  private String styledPrompt = "";
  private int promptLength;

  /**
   * Terminal row (relative to the prompt's first row) where the cursor was left by the last render.
   */
  private int cursorRow;

  Renderer(Terminal terminal, TerminalColors colors) {
    this.terminal = terminal;
    this.promptAnsi = colors.prompt == TerminalColors.NONE ? "" : Ansi.fg(colors.prompt, false);
    this.ghostAnsi = Ansi.fg(colors.ghostText, false);
  }

  /** Starts drawing a new line; the cursor is assumed to be at the start of the prompt's row. */
  void begin(String prompt) {
    this.styledPrompt = promptAnsi.isEmpty() ? prompt : promptAnsi + prompt + Ansi.RESET;
    this.promptLength = Ansi.visibleLength(prompt);
    this.cursorRow = 0;
  }

  void render(StringBuilder buffer, int cursor, String ghost, CompletionMenu menu, int width) {
    out.setLength(0);
    moveToInputStart();
    appendInputAndMenu(buffer, cursor, ghost, menu, width);
    terminal.write(out.toString());
    terminal.flush();
  }

  /**
   * Clears the active prompt/menu, writes {@code text} above it, and redraws the prompt/menu below.
   */
  void printAbove(
      CharSequence text,
      StringBuilder buffer,
      int cursor,
      String ghost,
      CompletionMenu menu,
      int width) {
    out.setLength(0);
    moveToInputStart();
    out.append(Ansi.CLEAR_BELOW);
    out.append(text);
    if (text.isEmpty() || text.charAt(text.length() - 1) != '\n') out.append('\n');
    cursorRow = 0;
    appendInputAndMenu(buffer, cursor, ghost, menu, width);
    terminal.write(out.toString());
    terminal.flush();
  }

  private void appendInputAndMenu(
      StringBuilder buffer, int cursor, String ghost, CompletionMenu menu, int width) {
    out.append(styledPrompt).append(buffer);
    if (!ghost.isEmpty()) out.append(ghostAnsi).append(ghost).append(Ansi.RESET);

    // Input may span several terminal rows. When it ends exactly at the right edge the terminal
    // keeps the
    // cursor in a "pending wrap" state, where erase sequences would eat the last character: force
    // the wrap.
    int totalLength = promptLength + buffer.length() + ghost.length();
    if (totalLength > 0 && totalLength % width == 0) out.append(" \r");
    int lastInputRow = totalLength / width;

    out.append(Ansi.CLEAR_LINE);
    int menuLines = menu.render(out, width);
    out.append(Ansi.CLEAR_BELOW);

    // Put the cursor back where the user is editing.
    int cursorPosition = promptLength + cursor;
    int targetRow = cursorPosition / width;
    int targetCol = cursorPosition % width;
    int rowsUp = menuLines + lastInputRow - targetRow;
    if (rowsUp > 0) out.append("\u001B[").append(rowsUp).append('A');
    out.append('\r');
    if (targetCol > 0) out.append("\u001B[").append(targetCol).append('C');

    cursorRow = targetRow;
  }

  /**
   * Redraws the final line without menu or ghost text, then appends {@code suffix} (e.g. a
   * newline).
   */
  void finish(StringBuilder buffer, String suffix) {
    out.setLength(0);
    moveToInputStart();
    // Clear first, then redraw: avoids the pending-wrap problem when the line fills the last
    // column.
    out.append(Ansi.CLEAR_BELOW);
    out.append(styledPrompt).append(buffer).append(suffix);
    cursorRow = 0;
    terminal.write(out.toString());
    terminal.flush();
  }

  /** Moves to column 0 of the row where the prompt starts. */
  private void moveToInputStart() {
    if (cursorRow > 0) out.append("\u001B[").append(cursorRow).append('A');
    out.append('\r');
  }
}
