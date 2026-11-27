package dev.sectile.boostrap.terminal.type;

import java.io.IOException;
import java.io.InputStreamReader;

/** Used when stdin/stdout are not a real console: plain line-less reads, no raw mode. */
final class FallbackTerminal implements Terminal {
  private final InputStreamReader reader = new InputStreamReader(System.in);

  @Override
  public void enterRawMode() {}

  @Override
  public void exitRawMode() {}

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

  @Override
  public void close() {}
}
