package dev.sectile.boostrap.logger.console;

import dev.sectile.boostrap.logger.ConsoleStyle;
import dev.sectile.boostrap.logger.LogAppender;
import dev.sectile.boostrap.logger.LoggerLevels;
import dev.sectile.boostrap.logger.MutableLogEntry;
import dev.sectile.kernel.logger.LogLevel;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.function.Consumer;

public final class ConsoleAppender implements LogAppender {
  private static final int MAX_RETAINED_BUFFER_CAPACITY = 16_384;

  private final PrintWriter writer;
  private final ConsoleStyle[] styles;

  private volatile Consumer<CharSequence> terminalPrinter;
  private volatile StringBuilder terminalBuffer;

  public ConsoleAppender(
      final PrintWriter writer,
      final LoggerLevels loggerLevels,
      final ConsoleStyle debugStyle,
      final ConsoleStyle logStyle,
      final ConsoleStyle infoStyle,
      final ConsoleStyle warnStyle,
      final ConsoleStyle errorStyle) {
    this.writer = writer;
    this.styles = new ConsoleStyle[LogLevel.getEntries().size()];
    this.styles[LogLevel.DEBUG.ordinal()] = loggerLevels.getDebug() ? debugStyle : null;
    this.styles[LogLevel.INFO.ordinal()] = loggerLevels.getInfo() ? infoStyle : null;
    this.styles[LogLevel.LOG.ordinal()] = loggerLevels.getLog() ? logStyle : null;
    this.styles[LogLevel.WARN.ordinal()] = loggerLevels.getWarn() ? warnStyle : null;
    this.styles[LogLevel.ERROR.ordinal()] = loggerLevels.getError() ? errorStyle : null;
  }

  public void attachTerminal(final Consumer<CharSequence> printer) {
    this.terminalBuffer = new StringBuilder(512);
    this.terminalPrinter = printer;
  }

  @Override
  public void append(MutableLogEntry entry) {
    final ConsoleStyle style = this.styles[entry.level];

    if (style == null) {
      return;
    }

    if (this.terminalPrinter != null) {
      appendTo(terminalBuffer, style, entry);
      return;
    }

    final PrintWriter out = this.writer;
    out.append(style.getPrefix())
        .append(entry.prefix)
        .append(style.getText())
        .append(entry.message);

    if (entry.throwable != null) {
      out.append('\n');
      entry.throwable.printStackTrace(out);
    }
    out.append(style.getAfterText()).append('\n');
    out.flush();
  }

  @Override
  public void flush() {
    final Consumer<CharSequence> printer = this.terminalPrinter;
    if (printer != null) {
      if (!terminalBuffer.isEmpty()) {
        printer.accept(terminalBuffer);
        terminalBuffer.setLength(0);
        if (terminalBuffer.capacity() > MAX_RETAINED_BUFFER_CAPACITY) {
          terminalBuffer.trimToSize();
        }
      }
      return;
    }
    writer.flush();
  }

  @Override
  public void close() {
    flush();
  }

  private static void appendTo(
      final StringBuilder sb, final ConsoleStyle style, final MutableLogEntry entry) {
    sb.append(style.getPrefix()).append(entry.prefix).append(style.getText()).append(entry.message);
    if (entry.throwable != null) {
      sb.append('\n');
      final StringWriter sw = new StringWriter();
      entry.throwable.printStackTrace(new PrintWriter(sw));
      sb.append(sw);
    }
    sb.append(style.getAfterText()).append('\n');
  }
}
