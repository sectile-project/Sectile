package dev.sectile.boostrap.terminal.config;

import dev.sectile.boostrap.terminal.LineReader;

/**
 * User-facing behavior and appearance options for {@link LineReader}. Plain mutable fields with
 * defaults; colors are resolved and {@link #maxMenuRows} is validated when the {@link LineReader}
 * is created, so set them before that.
 */
public final class TerminalConfig {
  /** Default prompt printed before user input. */
  public String defaultPrompt = "> ";

  /** Show suggestions automatically while typing. */
  public boolean autoCompleteWhileTyping = true;

  /** Allow opening and cycling suggestions with Tab / Shift+Tab. */
  public boolean tabCompletion = true;

  /** Show inline ghost completion to the right of the cursor. */
  public boolean inlineGhostSuggestion = true;

  /** Allow accepting the current ghost suggestion with Right arrow at end of line. */
  public boolean acceptWithRightArrow = true;

  /** Arrange suggestions in a multi-column grid filling the terminal width. */
  public boolean multiColumnMenu = true;

  /** Maximum number of visible rows in the completion menu (at least 1). */
  public int maxMenuRows = 6;

  /** Append a trailing space when accepting a completion at the end of the line. */
  public boolean appendSpaceOnAccept = true;

  /** Color palette. */
  public TerminalColors colors = new TerminalColors();
}
