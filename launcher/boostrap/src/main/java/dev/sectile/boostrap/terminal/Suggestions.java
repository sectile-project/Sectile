package dev.sectile.boostrap.terminal;

import dev.sectile.boostrap.terminal.completer.Completer;
import dev.sectile.boostrap.terminal.completer.Completions;
import dev.sectile.boostrap.terminal.config.TerminalConfig;

/**
 * Decides what to suggest for the text before the cursor: asks the {@link Completer}, feeds the
 * {@link CompletionMenu}, and applies the selected completion to the line. Knows nothing about keys
 * or drawing.
 */
final class Suggestions {
  final CompletionMenu menu;

  private final Completer completer; // may be null: no completion at all
  private final TerminalConfig config;

  /** Where the text replaced by the selected completion starts (it ends at the cursor). */
  private int start;

  Suggestions(Completer completer, TerminalConfig config) {
    this.completer = completer;
    this.config = config;
    this.menu = new CompletionMenu(config);
  }

  /**
   * Recomputes the passive menu for the word before the cursor. Call after every edit or cursor
   * move.
   */
  void refresh(StringBuilder buffer, int cursor) {
    if (!config.autoCompleteWhileTyping || completer == null) {
      menu.clear();
      return;
    }
    Completions completions = completer.complete(buffer.toString(), cursor);
    int wordStart = Math.max(0, Math.min(completions.start, cursor));
    String currentWord = buffer.substring(wordStart, cursor);
    boolean exactOnly =
        completions.items.size() == 1 && completions.items.get(0).equalsIgnoreCase(currentWord);

    if (currentWord.isEmpty() || exactOnly) {
      menu.clear();
    } else {
      start = wordStart;
      menu.show(completions.items);
    }
  }

  /** Focuses the menu (Tab / arrows), opening it first if it was not showing. */
  void open(StringBuilder buffer, int cursor, int width, boolean selectLast) {
    if (!menu.isOpen()) {
      if (completer == null) return;
      Completions completions = completer.complete(buffer.toString(), cursor);
      start = Math.max(0, Math.min(completions.start, cursor));
      menu.show(completions.items);
    }
    menu.focus(width, selectLast);
  }

  /**
   * Replaces the word with the selected item and closes the menu. Returns the new cursor, or -1 if
   * nothing is selected.
   */
  int accept(StringBuilder buffer, int cursor) {
    String match = menu.selectedMatch();
    if (match == null) return -1;
    String suffix = config.appendSpaceOnAccept && cursor == buffer.length() ? " " : "";
    buffer.replace(start, cursor, match + suffix);
    menu.clear();
    return start + match.length() + suffix.length();
  }

  /**
   * The part of the selected item that is not typed yet, shown dim after the cursor ("" if none).
   */
  String ghost(StringBuilder buffer, int cursor) {
    if (!config.inlineGhostSuggestion || cursor != buffer.length()) return "";
    String candidate = menu.selectedMatch();
    if (candidate == null) return "";
    String currentWord = buffer.substring(Math.min(start, cursor), cursor);
    if (candidate.length() > currentWord.length()
        && candidate.regionMatches(true, 0, currentWord, 0, currentWord.length())) {
      return candidate.substring(currentWord.length());
    }
    return "";
  }
}
