package dev.sectile.boostrap.terminal.type;

public interface Terminal extends AutoCloseable {
  void enterRawMode();

  void exitRawMode();

  /** Blocks until the next UTF-16 char is available; returns -1 at end of input. */
  int readChar();

  void write(String text);

  void flush();

  default int width() {
    return 80;
  }

  default boolean hasBufferedInput() {
    return false;
  }

  @Override
  void close();

  /** The native terminal of the current platform. */
  static Terminal system() {
    Terminal terminal;
    if (System.getProperty("os.name").toLowerCase().contains("win")) {
      terminal = WindowsTerminal.tryCreate();

      if (terminal == null) {
        terminal = new FallbackTerminal();
      }
    } else {
      terminal = new UnixTerminal();
    }

    return terminal;
  }
}
