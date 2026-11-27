package dev.sectile.boostrap.terminal.config;

import java.util.Locale;
import java.util.regex.Pattern;

public final class Ansi {
  public static final String RESET = "\u001B[0m";
  public static final String CLEAR_LINE = "\u001B[K";
  public static final String CLEAR_BELOW = "\u001B[J";

  private static final Pattern ANSI_PATTERN = Pattern.compile("\u001B\\[[;\\d]*[ -/]*[@-~]");

  /**
   * Detected once: 24-bit color everywhere except macOS Terminal.app (unless it advertises
   * support).
   */
  private static final boolean TRUE_COLOR = detectTrueColor();

  private Ansi() {}

  /**
   * Converts an RGB color in {@code 0xRRGGBB} form into an ANSI foreground escape sequence. Uses
   * 24-bit TrueColor by default and falls back to xterm 256-color on terminals like macOS
   * Terminal.app.
   */
  public static String fg(int rgb, boolean bold) {
    int r = rgb >> 16 & 0xFF;
    int g = rgb >> 8 & 0xFF;
    int b = rgb & 0xFF;
    String style = bold ? "1;" : "";
    return TRUE_COLOR
        ? "\u001B[" + style + "38;2;" + r + ";" + g + ";" + b + "m"
        : "\u001B[" + style + "38;5;" + rgbToAnsi256(r, g, b) + "m";
  }

  public static int visibleLength(String text) {
    return ANSI_PATTERN.matcher(text).replaceAll("").length();
  }

  private static boolean detectTrueColor() {
    String colorTerm = System.getenv("COLORTERM");
    if (colorTerm != null) {
      colorTerm = colorTerm.toLowerCase(Locale.ROOT);
      if (colorTerm.equals("truecolor") || colorTerm.equals("24bit")) return true;
    }
    return !"Apple_Terminal".equals(System.getenv("TERM_PROGRAM"));
  }

  private static int rgbToAnsi256(int r, int g, int b) {
    if (r == g && g == b) {
      if (r < 8) return 16;
      if (r > 248) return 231;
      return 232 + ((r - 8) * 24 + 123) / 247;
    }
    // Integer rounding of v / 255 * 5, no floating point needed.
    return 16 + 36 * level(r) + 6 * level(g) + level(b);
  }

  private static int level(int v) {
    return (v * 5 + 127) / 255;
  }
}
