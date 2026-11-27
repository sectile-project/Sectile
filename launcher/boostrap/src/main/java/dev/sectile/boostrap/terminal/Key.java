package dev.sectile.boostrap.terminal;

/**
 * High-level key events. For {@link #PRINTABLE}, the character is in {@link KeyReader#lastChar}.
 */
enum Key {
  PRINTABLE,
  ENTER,
  TAB,
  SHIFT_TAB,
  BACKSPACE,
  DELETE,
  ESCAPE,
  UP,
  DOWN,
  LEFT,
  RIGHT,
  HOME,
  END,
  CTRL_C,
  CTRL_D,
  EOF,
  UNKNOWN
}
