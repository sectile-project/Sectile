package dev.sectile.boostrap.terminal.type;

import java.io.BufferedOutputStream;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;

/**
 * Windows native console via the Java Foreign Function & Memory API (kernel32.dll). Zero external
 * dependencies and no C compilation needed.
 */
final class WindowsTerminal implements Terminal {
  private static final int DEFAULT_WIDTH = 80;
  private static final int MIN_WIDTH = 20;
  private static final int CP_UTF8 = 65001;
  private static final int STD_INPUT_HANDLE = -10;
  private static final int STD_OUTPUT_HANDLE = -11;
  private static final int ENABLE_PROCESSED_INPUT = 0x0001;
  private static final int ENABLE_LINE_INPUT = 0x0002;
  private static final int ENABLE_ECHO_INPUT = 0x0004;
  private static final int ENABLE_VIRTUAL_TERMINAL_PROCESSING = 0x0004;
  private static final int KEY_EVENT = 0x0001;
  private static final int SHIFT_PRESSED = 0x0010;
  private static final int VK_END = 0x23;
  private static final int VK_HOME = 0x24;
  private static final int VK_LEFT = 0x25;
  private static final int VK_UP = 0x26;
  private static final int VK_RIGHT = 0x27;
  private static final int VK_DOWN = 0x28;
  private static final int VK_DELETE = 0x2E;

  private static final Linker LINKER = Linker.nativeLinker();
  private static final SymbolLookup KERNEL32 =
      SymbolLookup.libraryLookup("kernel32", Arena.global());

  private static final MethodHandle GET_STD_HANDLE =
      function("GetStdHandle", FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
  private static final MethodHandle GET_CONSOLE_MODE =
      function(
          "GetConsoleMode",
          FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
  private static final MethodHandle SET_CONSOLE_MODE =
      function(
          "SetConsoleMode",
          FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
  private static final MethodHandle GET_CONSOLE_OUTPUT_CP =
      function("GetConsoleOutputCP", FunctionDescriptor.of(ValueLayout.JAVA_INT));
  private static final MethodHandle SET_CONSOLE_OUTPUT_CP =
      function(
          "SetConsoleOutputCP", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
  private static final MethodHandle READ_CONSOLE_INPUT_W =
      function(
          "ReadConsoleInputW",
          FunctionDescriptor.of(
              ValueLayout.JAVA_INT,
              ValueLayout.ADDRESS,
              ValueLayout.ADDRESS,
              ValueLayout.JAVA_INT,
              ValueLayout.ADDRESS));
  private static final MethodHandle GET_CONSOLE_SCREEN_BUFFER_INFO =
      function(
          "GetConsoleScreenBufferInfo",
          FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS));

  private final MemorySegment inHandle;
  private final MemorySegment outHandle;
  private final int origInMode;
  private final int origOutMode;

  private final Arena arena = Arena.ofShared();
  private final MemorySegment inputRecord = arena.allocate(20, 8);
  private final MemorySegment readCount = arena.allocate(ValueLayout.JAVA_INT);
  // CONSOLE_SCREEN_BUFFER_INFO is 22 bytes; srWindow.Left (offset 10) and srWindow.Right (offset
  // 14) are shorts
  private final MemorySegment screenInfo = arena.allocate(22, 8);

  // Always UTF-8, independent of the JVM's default stdout encoding (needed for the menu's arrow
  // glyphs).
  private final PrintStream out =
      new PrintStream(
          new BufferedOutputStream(new FileOutputStream(FileDescriptor.out)),
          false,
          StandardCharsets.UTF_8);

  /** Escape-sequence tail still to be returned after a synthetic ESC (e.g. "[A"). */
  private String pending = "";

  private int pendingPos;

  private int origOutputCodePage;
  private boolean rawMode;

  private WindowsTerminal(
      MemorySegment inHandle, MemorySegment outHandle, int origInMode, int origOutMode) {
    this.inHandle = inHandle;
    this.outHandle = outHandle;
    this.origInMode = origInMode;
    this.origOutMode = origOutMode;
  }

  /** Returns null when stdin/stdout are not a real console (redirected, IDE, ...). */
  static WindowsTerminal tryCreate() {
    try {
      MemorySegment in = getStdHandle(STD_INPUT_HANDLE);
      MemorySegment out = getStdHandle(STD_OUTPUT_HANDLE);
      if (in.equals(MemorySegment.NULL) || out.equals(MemorySegment.NULL)) return null;

      try (Arena tmp = Arena.ofConfined()) {
        MemorySegment inMode = tmp.allocate(ValueLayout.JAVA_INT);
        MemorySegment outMode = tmp.allocate(ValueLayout.JAVA_INT);
        if (getConsoleMode(in, inMode) == 0 || getConsoleMode(out, outMode) == 0) return null;
        return new WindowsTerminal(
            in, out, inMode.get(ValueLayout.JAVA_INT, 0), outMode.get(ValueLayout.JAVA_INT, 0));
      }
    } catch (Exception | LinkageError e) {
      return null;
    }
  }

  @Override
  public void enterRawMode() {
    if (rawMode) return;
    int rawIn = origInMode & ~(ENABLE_LINE_INPUT | ENABLE_ECHO_INPUT | ENABLE_PROCESSED_INPUT);
    setConsoleMode(inHandle, rawIn);
    setConsoleMode(outHandle, origOutMode | ENABLE_VIRTUAL_TERMINAL_PROCESSING);
    origOutputCodePage = getConsoleOutputCP();
    setConsoleOutputCP(CP_UTF8);
    rawMode = true;
  }

  @Override
  public void exitRawMode() {
    if (!rawMode) return;
    setConsoleMode(inHandle, origInMode);
    setConsoleMode(outHandle, origOutMode);
    if (origOutputCodePage != 0) setConsoleOutputCP(origOutputCodePage);
    rawMode = false;
  }

  @Override
  public int readChar() {
    if (pendingPos < pending.length()) return pending.charAt(pendingPos++);

    while (true) {
      if (readConsoleInput(inHandle, inputRecord, 1, readCount) == 0
          || readCount.get(ValueLayout.JAVA_INT, 0) == 0) {
        return -1;
      }
      if (inputRecord.get(ValueLayout.JAVA_SHORT, 0) != KEY_EVENT) continue;
      if (inputRecord.get(ValueLayout.JAVA_INT, 4) == 0) continue; // key up

      int unicodeChar = inputRecord.get(ValueLayout.JAVA_CHAR, 14);
      int controlKeyState = inputRecord.get(ValueLayout.JAVA_INT, 16);
      if (unicodeChar == '\t' && (controlKeyState & SHIFT_PRESSED) != 0) {
        return startSequence("[Z");
      }
      if (unicodeChar != 0) return unicodeChar;

      int virtualKey = inputRecord.get(ValueLayout.JAVA_SHORT, 10) & 0xFFFF;
      String sequence =
          switch (virtualKey) {
            case VK_UP -> "[A";
            case VK_DOWN -> "[B";
            case VK_RIGHT -> "[C";
            case VK_LEFT -> "[D";
            case VK_HOME -> "[H";
            case VK_END -> "[F";
            case VK_DELETE -> "[3~";
            default -> null;
          };
      if (sequence != null) return startSequence(sequence);
    }
  }

  /** Emulates a VT key sequence: returns ESC now and the rest of [sequence] on the next reads. */
  private int startSequence(String sequence) {
    pending = sequence;
    pendingPos = 0;
    return 27;
  }

  @Override
  public void write(String text) {
    out.print(text);
  }

  @Override
  public void flush() {
    out.flush();
  }

  @Override
  public int width() {
    try {
      if (getConsoleScreenBufferInfo(outHandle, screenInfo) == 0) return DEFAULT_WIDTH;
      int left = screenInfo.get(ValueLayout.JAVA_SHORT, 10);
      int right = screenInfo.get(ValueLayout.JAVA_SHORT, 14);
      return Math.max(right - left + 1, MIN_WIDTH);
    } catch (RuntimeException e) {
      return DEFAULT_WIDTH;
    }
  }

  @Override
  public boolean hasBufferedInput() {
    return pendingPos < pending.length();
  }

  @Override
  public void close() {
    exitRawMode();
    out.flush();
    if (arena.scope().isAlive()) arena.close();
  }

  private static MethodHandle function(String name, FunctionDescriptor descriptor) {
    return LINKER.downcallHandle(KERNEL32.find(name).orElseThrow(), descriptor);
  }

  private static MemorySegment getStdHandle(int id) {
    try {
      return (MemorySegment) GET_STD_HANDLE.invokeExact(id);
    } catch (Throwable t) {
      throw rethrow(t);
    }
  }

  private static int getConsoleMode(MemorySegment handle, MemorySegment modePtr) {
    try {
      return (int) GET_CONSOLE_MODE.invokeExact(handle, modePtr);
    } catch (Throwable t) {
      throw rethrow(t);
    }
  }

  private static int setConsoleMode(MemorySegment handle, int mode) {
    try {
      return (int) SET_CONSOLE_MODE.invokeExact(handle, mode);
    } catch (Throwable t) {
      throw rethrow(t);
    }
  }

  private static int getConsoleOutputCP() {
    try {
      return (int) GET_CONSOLE_OUTPUT_CP.invokeExact();
    } catch (Throwable t) {
      throw rethrow(t);
    }
  }

  private static int setConsoleOutputCP(int codePage) {
    try {
      return (int) SET_CONSOLE_OUTPUT_CP.invokeExact(codePage);
    } catch (Throwable t) {
      throw rethrow(t);
    }
  }

  private static int readConsoleInput(
      MemorySegment handle, MemorySegment record, int length, MemorySegment count) {
    try {
      return (int) READ_CONSOLE_INPUT_W.invokeExact(handle, record, length, count);
    } catch (Throwable t) {
      throw rethrow(t);
    }
  }

  private static int getConsoleScreenBufferInfo(MemorySegment handle, MemorySegment info) {
    try {
      return (int) GET_CONSOLE_SCREEN_BUFFER_INFO.invokeExact(handle, info);
    } catch (Throwable t) {
      throw rethrow(t);
    }
  }

  private static RuntimeException rethrow(Throwable t) {
    if (t instanceof RuntimeException e) throw e;
    if (t instanceof Error e) throw e;
    return new IllegalStateException(t);
  }
}
