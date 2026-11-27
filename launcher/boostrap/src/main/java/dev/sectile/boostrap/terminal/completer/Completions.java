package dev.sectile.boostrap.terminal.completer;

import java.util.List;

/** Result of a completion request. */
public final class Completions {

  /**
   * Index in the line where the text to be replaced begins; accepting an item replaces {@code
   * start..cursor}.
   */
  public final int start;

  /** Candidate replacements, already in display order. */
  public final List<String> items;

  public Completions(int start, List<String> items) {
    this.start = start;
    this.items = items;
  }
}
