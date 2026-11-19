package dev.sectile.boostrap.logger.console;

import dev.sectile.boostrap.logger.ConsoleStyle;
import dev.sectile.boostrap.logger.LogAppender;
import dev.sectile.boostrap.logger.LoggerLevels;
import dev.sectile.boostrap.logger.MutableLogEntry;
import dev.sectile.kernel.logger.LogLevel;
import java.io.PrintWriter;
import java.io.StringWriter;
import org.jetbrains.annotations.Nullable;
import org.jline.reader.LineReader;

public final class ConsoleAppender implements LogAppender {
  private volatile PrintWriter writer;
  private volatile @Nullable LineReader lineReader;
  private final ConsoleStyle[] styles;
  private final StringBuilder lineBuffer = new StringBuilder(256);

  public ConsoleAppender(
      final PrintWriter writer,
      final @Nullable LineReader lineReader,
      final LoggerLevels loggerLevels,
      final ConsoleStyle debugStyle,
      final ConsoleStyle logStyle,
      final ConsoleStyle infoStyle,
      final ConsoleStyle warnStyle,
      final ConsoleStyle errorStyle) {
    this.writer = writer;
    this.lineReader = lineReader;
    this.styles = new ConsoleStyle[LogLevel.getEntries().size()];
    this.styles[LogLevel.DEBUG.ordinal()] = loggerLevels.getDebug() ? debugStyle : null;
    this.styles[LogLevel.INFO.ordinal()] = loggerLevels.getInfo() ? infoStyle : null;
    this.styles[LogLevel.LOG.ordinal()] = loggerLevels.getLog() ? logStyle : null;
    this.styles[LogLevel.WARN.ordinal()] = loggerLevels.getWarn() ? warnStyle : null;
    this.styles[LogLevel.ERROR.ordinal()] = loggerLevels.getError() ? errorStyle : null;
  }

  public void attachJLine(final PrintWriter writer, final LineReader lineReader) {
    this.writer = writer;
    this.lineReader = lineReader;
  }

  @Override
  public void append(MutableLogEntry entry) {
    final ConsoleStyle style = this.styles[entry.level];

    if (style == null) {
      return;
    }

    final LineReader reader = this.lineReader;
    if (reader != null && reader.isReading()) {
      lineBuffer.setLength(0);
      lineBuffer
          .append(style.getPrefix())
          .append(entry.prefix)
          .append(style.getText())
          .append(entry.message);

      if (entry.throwable != null) {
        final StringWriter sw = new StringWriter();
        entry.throwable.printStackTrace(new PrintWriter(sw));
        lineBuffer.append('\n').append(sw);
      }
      lineBuffer.append(style.getAfterText());
      // printAbove clears the active prompt, prints the log line, and redraws the prompt below it.
      reader.printAbove(lineBuffer.toString());
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
    writer.flush();
  }

  @Override
  public void close() {}
}
