package dev.sectile.boostrap.terminal.type;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class UnixTerminal implements Terminal {
  private static final int DEFAULT_WIDTH = 80;
  private static final int MIN_WIDTH = 20;
  private static final long WIDTH_CACHE_NANOS = 250_000_000L;

  private final InputStreamReader reader = new InputStreamReader(System.in);
  private String savedStty;

  private int cachedWidth = DEFAULT_WIDTH;
  private long widthCheckedAt;
  private boolean widthKnown;

  @Override
  public void enterRawMode() {
    if (savedStty != null) return;
    String saved = runStty("-g");
    savedStty = saved == null ? null : saved.trim();
    runStty("-icanon", "-echo", "-isig", "min", "1", "time", "0");
    widthKnown = false; // pick up resizes between prompts
  }

  @Override
  public void exitRawMode() {
    if (savedStty == null) return;
    runStty(savedStty);
    savedStty = null;
  }

  @Override
  public int readChar() {
    try {
      return reader.read();
    } catch (IOException e) {
      return -1;
    }
  }

  @Override
  public void write(String text) {
    System.out.print(text);
  }

  @Override
  public void flush() {
    System.out.flush();
  }

  /**
   * {@code stty size} spawns a process, so the result is cached briefly instead of queried on every
   * key press.
   */
  @Override
  public int width() {
    long now = System.nanoTime();
    if (!widthKnown || now - widthCheckedAt > WIDTH_CACHE_NANOS) {
      cachedWidth = queryWidth();
      widthCheckedAt = now;
      widthKnown = true;
    }
    return cachedWidth;
  }

  @Override
  public boolean hasBufferedInput() {
    try {
      return reader.ready() || System.in.available() > 0;
    } catch (IOException e) {
      return false;
    }
  }

  @Override
  public void close() {
    exitRawMode();
  }

  private int queryWidth() {
    String size = runStty("size"); // "rows cols"
    if (size == null) return DEFAULT_WIDTH;
    String[] parts = size.trim().split(" ");
    if (parts.length < 2) return DEFAULT_WIDTH;
    try {
      return Math.max(Integer.parseInt(parts[1]), MIN_WIDTH);
    } catch (NumberFormatException e) {
      return DEFAULT_WIDTH;
    }
  }

  private static String runStty(String... args) {
    try {
      List<String> command = new ArrayList<>(args.length + 1);
      command.add("stty");
      Collections.addAll(command, args);
      Process process =
          new ProcessBuilder(command).redirectInput(ProcessBuilder.Redirect.INHERIT).start();
      String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
      process.waitFor();
      return output;
    } catch (IOException e) {
      return null;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return null;
    }
  }
}
