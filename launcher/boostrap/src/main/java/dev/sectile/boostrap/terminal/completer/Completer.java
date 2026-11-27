package dev.sectile.boostrap.terminal.completer;

public interface Completer {
  /**
   * Returns candidate completions for the text that ends at {@code cursor} in {@code line},
   * together with where that text starts (see {@link Completions#start}).
   */
  Completions complete(String line, int cursor);
}
