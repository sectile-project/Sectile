package dev.sectile.boostrap.terminal.config;

import dev.sectile.boostrap.terminal.LineReader;

/**
 * RGB color palette ({@code 0xRRGGBB} values). Plain mutable fields: set them before creating the
 * {@link LineReader}.
 */
public final class TerminalColors {
  /** Use as {@link #prompt} to leave the prompt in the terminal's default color. */
  public static final int NONE = -1;

  public int prompt = NONE;
  public int ghostText = 0x6B7280;
  public int menuItem = 0x6B7280;
  public int selectedItem = 0xFACC15;
  public int scrollHint = 0x6B7280;
  public boolean boldSelectedItem = true;
}
