package dev.sectile.boostrap.terminal.completer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/** Completes the word before the cursor from a fixed set of strings. */
public final class StringsCompleter implements Completer {
  private final List<String> sorted;
  private final boolean ignoreCase;

  public StringsCompleter(String... candidates) {
    this(List.of(candidates), false);
  }

  public StringsCompleter(Collection<String> candidates, boolean ignoreCase) {
    this.ignoreCase = ignoreCase;
    // Sorted once, not on every keystroke.
    Comparator<String> order =
        ignoreCase ? String.CASE_INSENSITIVE_ORDER : Comparator.naturalOrder();
    this.sorted = candidates.stream().distinct().sorted(order).toList();
  }

  @Override
  public Completions complete(String line, int cursor) {
    int start = line.lastIndexOf(' ', cursor - 1) + 1;
    String prefix = line.substring(start, cursor);
    List<String> items = new ArrayList<>();
    for (String candidate : sorted) {
      if (candidate.regionMatches(ignoreCase, 0, prefix, 0, prefix.length())) items.add(candidate);
    }
    return new Completions(start, items);
  }
}
