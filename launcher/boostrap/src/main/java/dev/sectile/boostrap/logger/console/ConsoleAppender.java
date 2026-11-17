package dev.sectile.boostrap.logger.console;

import dev.sectile.boostrap.logger.ConsoleStyle;
import dev.sectile.boostrap.logger.LogAppender;
import dev.sectile.boostrap.logger.LoggerLevels;
import dev.sectile.boostrap.logger.MutableLogEntry;
import dev.sectile.kernel.logger.LogLevel;
import java.io.PrintWriter;

public final class ConsoleAppender implements LogAppender {
  private final PrintWriter writer;
  private final ConsoleStyle[] styles;

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

  @Override
  public void append(MutableLogEntry entry) {
    final ConsoleStyle style = this.styles[entry.level];

    if (style == null) {
      return;
    }

    writer
        .append('\n')
        .append(style.getPrefix())
        .append(entry.prefix)
        .append(style.getText())
        .append(entry.message);

    if (entry.throwable != null) {
      writer.append('\n');
      entry.throwable.printStackTrace(writer);
    }
    writer.append(style.getAfterText());

    writer.flush();
  }

  @Override
  public void flush() {
    writer.flush();
  }

  @Override
  public void close() {}
}
