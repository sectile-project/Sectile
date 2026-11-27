package dev.sectile.boostrap.terminal;

import dev.sectile.boostrap.terminal.config.Ansi;
import dev.sectile.boostrap.terminal.config.TerminalColors;
import dev.sectile.boostrap.terminal.config.TerminalConfig;
import java.util.List;

/** Completion menu state, 2D grid navigation, viewport scrolling, and ANSI rendering. */
final class CompletionMenu {
  private static final int CELL_PADDING = 4;

  private final TerminalConfig config;
  private final String itemAnsi;
  private final String selectedAnsi;
  private final String hintAnsi;

  List<String> matches = List.of();
  int selected;

  /** True while the user is navigating the menu (arrows, Enter and Tab act on it). */
  boolean focused;

  private int maxItemLength;
  private int scrollRow;

  // Grid dimensions for the width passed to the last layout() call. Only valid while the menu is
  // open.
  private int cols;
  private int totalRows;
  private int visibleRows;

  CompletionMenu(TerminalConfig config) {
    this.config = config;
    TerminalColors colors = config.colors;
    this.itemAnsi = Ansi.fg(colors.menuItem, false);
    this.selectedAnsi = Ansi.fg(colors.selectedItem, colors.boldSelectedItem);
    this.hintAnsi = Ansi.fg(colors.scrollHint, false);
  }

  boolean isOpen() {
    return !matches.isEmpty();
  }

  /** The highlighted item, or null when the menu is closed. */
  String selectedMatch() {
    return isOpen() ? matches.get(selected) : null;
  }

  void clear() {
    matches = List.of();
    maxItemLength = 0;
    selected = 0;
    scrollRow = 0;
    focused = false;
  }

  /** Shows {@code items} without taking focus (passive mode). An empty list closes the menu. */
  void show(List<String> items) {
    if (items.isEmpty()) {
      clear();
      return;
    }
    matches = items;
    maxItemLength = 0;
    for (String item : items) maxItemLength = Math.max(maxItemLength, item.length());
    selected = 0;
    scrollRow = 0;
    focused = false;
  }

  /**
   * Gives focus to the open menu, optionally jumping to the last item. No-op if the menu is closed.
   */
  void focus(int width, boolean selectLast) {
    if (!isOpen()) return;
    focused = true;
    if (selectLast) selected = matches.size() - 1;
    updateScroll(width);
  }

  void next(int width) {
    if (!isOpen()) return;
    select((selected + 1) % matches.size(), width);
  }

  void previous(int width) {
    if (!isOpen()) return;
    select((selected - 1 + matches.size()) % matches.size(), width);
  }

  void moveDown(int width) {
    if (!isOpen()) return;
    layout(width);
    int next = selected + cols;
    select(next < matches.size() ? next : selected % cols, width);
  }

  void moveUp(int width) {
    if (!isOpen()) return;
    layout(width);
    int prev = selected - cols;
    if (prev < 0) {
      // Wrap to the bottom of the same column (one row up if the last row is short).
      prev = (totalRows - 1) * cols + selected % cols;
      if (prev >= matches.size()) prev -= cols;
    }
    select(prev, width);
  }

  /** Appends the rendered menu lines to {@code sb} and returns the number of lines rendered. */
  int render(StringBuilder sb, int width) {
    if (!isOpen()) return 0;

    layout(width);
    int cellWidth = width / cols;
    int startRow = Math.max(0, Math.min(scrollRow, totalRows - visibleRows));
    int endRow = startRow + visibleRows;

    for (int row = startRow; row < endRow; row++) {
      sb.append("\n\r");
      int rowStart = row * cols;
      int rowEnd = Math.min(rowStart + cols, matches.size());
      for (int i = rowStart; i < rowEnd; i++) {
        String item = matches.get(i);
        boolean isSelected = focused && i == selected;
        String prefix = isSelected ? "> " : "  ";

        sb.append(isSelected ? selectedAnsi : itemAnsi)
            .append(prefix)
            .append(item)
            .append(Ansi.RESET);
        if (i < rowEnd - 1) {
          int padding = Math.max(cellWidth - prefix.length() - item.length(), 2);
          for (int p = 0; p < padding; p++) sb.append(' ');
        }
      }
      sb.append(Ansi.CLEAR_LINE);
    }

    int lines = visibleRows;
    if (totalRows > visibleRows) {
      sb.append("\n\r")
          .append(hintAnsi)
          .append("  ")
          .append(startRow > 0 ? '↑' : ' ')
          .append(endRow < totalRows ? '↓' : ' ')
          .append(" rows ")
          .append(startRow + 1)
          .append('-')
          .append(endRow)
          .append(" of ")
          .append(totalRows)
          .append(Ansi.RESET)
          .append(Ansi.CLEAR_LINE);
      lines++;
    }
    return lines;
  }

  private void layout(int width) {
    cols = config.multiColumnMenu ? Math.max(1, width / (maxItemLength + CELL_PADDING)) : 1;
    totalRows = (matches.size() + cols - 1) / cols;
    visibleRows = Math.min(totalRows, config.maxMenuRows);
  }

  private void select(int index, int width) {
    selected = index;
    updateScroll(width);
  }

  private void updateScroll(int width) {
    if (!isOpen()) {
      scrollRow = 0;
      return;
    }
    layout(width);
    int selectedRow = selected / cols;
    if (selectedRow < scrollRow) {
      scrollRow = selectedRow;
    } else if (selectedRow >= scrollRow + visibleRows) {
      scrollRow = selectedRow - visibleRows + 1;
    }
    scrollRow = Math.max(0, Math.min(scrollRow, totalRows - visibleRows));
  }
}
