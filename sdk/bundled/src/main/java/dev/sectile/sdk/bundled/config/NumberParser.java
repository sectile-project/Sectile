package dev.sectile.sdk.bundled.config;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;

public final class NumberParser {

  private static final double[] POW10 = new double[309];
  private static final long[] LONG_POW10 = new long[19];
  public static final byte[] HEX_DIGITS = new byte[128];

  /** Every integer up to 2^53 is exactly representable as a double. */
  private static final long MAX_EXACT_MANTISSA = 1L << 53;

  /** 10^22 is the largest power of ten exactly representable as a double. */
  private static final int MAX_EXACT_POW10 = 22;

  static {
    for (int i = 0; i < POW10.length; i++) {
      POW10[i] = Double.parseDouble("1e" + i);
    }
    long p = 1L;
    for (int i = 0; i < LONG_POW10.length; i++) {
      LONG_POW10[i] = p;
      p *= 10L;
    }
    java.util.Arrays.fill(HEX_DIGITS, (byte) -1);
    for (int i = '0'; i <= '9'; i++) HEX_DIGITS[i] = (byte) (i - '0');
    for (int i = 'a'; i <= 'f'; i++) HEX_DIGITS[i] = (byte) (i - 'a' + 10);
    for (int i = 'A'; i <= 'F'; i++) HEX_DIGITS[i] = (byte) (i - 'A' + 10);
  }

  private NumberParser() {}

  /** Fastjson2 digit2 optimization: parses 2 digits at once or returns -1. */
  public static int digit2(final byte[] buf, final int offset) {
    final int d0 = buf[offset] - '0';
    final int d1 = buf[offset + 1] - '0';
    if ((d0 | d1 | (9 - d0) | (9 - d1)) < 0) {
      return -1;
    }
    return (d0 << 3) + (d0 << 1) + d1;
  }

  /**
   * Correctly rounded IEEE 754 conversion of {@code -negMantissa * 10^-scale} (sign applied by
   * {@code negative}). The mantissa must be exact (no dropped digits).
   *
   * <p>Fast path (Clinger): when the mantissa is at most 2^53 and |scale| is at most 22, both
   * operands are exact doubles and a single multiplication or division is correctly rounded.
   * Everything else goes through {@link BigDecimal}, which is also correctly rounded (including
   * subnormals and overflow to infinity).
   */
  public static double toDouble(final long negMantissa, final int scale, final boolean negative) {
    if (negMantissa == 0) {
      return negative ? -0.0 : 0.0;
    }
    if (negMantissa != Long.MIN_VALUE && scale >= -MAX_EXACT_POW10 && scale <= MAX_EXACT_POW10) {
      final long mag = -negMantissa;
      if (mag <= MAX_EXACT_MANTISSA) {
        final double v = (double) mag;
        final double r = scale == 0 ? v : (scale < 0 ? v * POW10[-scale] : v / POW10[scale]);
        return negative ? -r : r;
      }
    }
    final double d = BigDecimal.valueOf(negMantissa, scale).doubleValue();
    return negative ? d : -d;
  }

  /** Same as {@link #toDouble(long, int, boolean)} but taking a non-negative mantissa. */
  @Deprecated
  public static double toDoublePositive(
      final long mantissa, final int scale, final boolean negative) {
    return toDouble(-mantissa, scale, negative);
  }

  public static Number toCompactInteger(final long negMantissa, final boolean negative) {
    if (negative) {
      if (negMantissa >= Integer.MIN_VALUE) {
        return (int) negMantissa;
      }
      return negMantissa;
    } else {
      final long pos = -negMantissa;
      if (pos <= Integer.MAX_VALUE) {
        return (int) pos;
      }
      return pos;
    }
  }

  // JSON Strict Number Parser (RFC 8259)
  public static Number readJsonNumber(final ByteReader r) {
    final byte[] buf = r.buf;
    final int end = r.end;
    final int numStart = r.pos;
    int p = numStart;

    boolean negative = false;
    byte b = buf[p];
    if (b == '-') {
      negative = true;
      if (++p >= end) throw r.error("incomplete JSON number");
      b = buf[p];
    }

    // Ultra-fast path for single-digit integers (0..9) followed by ',', ']', '}', or whitespace
    final int firstDigit = b - '0';
    if (firstDigit >= 0 && firstDigit <= 9) {
      if (p + 1 < end) {
        final byte next = buf[p + 1];
        if (next == ','
            || next == ']'
            || next == '}'
            || next == ' '
            || next == '\n'
            || next == '\r'
            || next == '\t') {
          r.pos = p + 1;
          return negative
              ? (firstDigit == 0 ? 0 : Integer.valueOf(-firstDigit))
              : Integer.valueOf(firstDigit);
        }
      } else {
        r.pos = p + 1;
        return negative
            ? (firstDigit == 0 ? 0 : Integer.valueOf(-firstDigit))
            : Integer.valueOf(firstDigit);
      }
    } else {
      r.pos = p;
      throw r.error("invalid JSON number");
    }

    final int intStart = p;
    long mantissa = firstDigit;
    p++;

    if (firstDigit == 0) {
      if (p < end && buf[p] >= '0' && buf[p] <= '9') {
        r.pos = p;
        throw r.error("leading zeros are not allowed in JSON numbers");
      }
    } else {
      // Unchecked fast loop for up to 18 digits (cannot overflow 64-bit positive long!)
      final int safeEnd = Math.min(end - 1, intStart + 17);
      int d2;
      while (p < safeEnd && (d2 = digit2(buf, p)) != -1) {
        mantissa = mantissa * 100 + d2;
        p += 2;
      }
      if (p < intStart + 18 && p < end) {
        final int d = buf[p] - '0';
        if (d >= 0 && d <= 9) {
          mantissa = mantissa * 10 + d;
          p++;
        }
      }
    }

    // Fast exit if integer with <= 18 digits (no '.', 'e', 'E', and no more digits)
    if (p >= end || ((b = buf[p]) != '.' && (b | 0x20) != 'e' && (b < '0' || b > '9'))) {
      r.pos = p;
      final long signed = negative ? -mantissa : mantissa;
      if ((int) signed == signed) {
        return (int) signed;
      }
      return signed;
    }

    return readJsonFloatOrBigInt(r, buf, p, end, mantissa, numStart, intStart, negative);
  }

  /**
   * Slow path: floats, and integers with 19+ digits. {@code mantissa} holds the integer digits
   * consumed so far (at most 18, all exact).
   */
  private static Number readJsonFloatOrBigInt(
      final ByteReader r,
      final byte[] buf,
      int p,
      final int end,
      long mantissa,
      final int numStart,
      final int intStart,
      final boolean negative) {

    boolean isFloat = false;
    boolean truncated = false; // a non-zero significant digit was dropped: mantissa is inexact
    int scale = 0; // value = mantissa * 10^-scale
    // Significant digits held by mantissa (leading zeros do not count).
    int sig = mantissa == 0 ? 0 : p - intStart;

    // Remaining integer digits (19+ digit integers)
    while (p < end) {
      final int d = buf[p] - '0';
      if (d < 0 || d > 9) break;
      p++;
      if (sig < 18) {
        mantissa = mantissa * 10 + d;
        sig++;
      } else {
        scale--;
        if (d != 0) truncated = true;
      }
    }

    // Fractional part
    if (p < end && buf[p] == '.') {
      isFloat = true;
      p++;
      if (p >= end || buf[p] < '0' || buf[p] > '9') {
        r.pos = p;
        throw r.error("expected at least one digit after '.'");
      }
      while (p < end) {
        // 2-digit step, only once the mantissa is non-zero so that leading zeros of the
        // fraction (0.000123) do not eat the significant-digit budget.
        if (mantissa != 0 && sig <= 16 && p + 1 < end) {
          final int d2 = digit2(buf, p);
          if (d2 != -1) {
            mantissa = mantissa * 100 + d2;
            p += 2;
            scale += 2;
            sig += 2;
            continue;
          }
        }
        final int d = buf[p] - '0';
        if (d < 0 || d > 9) break;
        p++;
        if (sig < 18) {
          mantissa = mantissa * 10 + d;
          scale++;
          if (mantissa != 0) sig++;
        } else if (d != 0) {
          truncated = true;
        }
      }
    }

    // Exponent part
    if (p < end && (buf[p] | 0x20) == 'e') {
      isFloat = true;
      p++;
      boolean expNegative = false;
      if (p < end && (buf[p] == '-' || buf[p] == '+')) {
        expNegative = (buf[p] == '-');
        p++;
      }
      if (p >= end || buf[p] < '0' || buf[p] > '9') {
        r.pos = p;
        throw r.error("invalid exponent in JSON number");
      }
      int exp = 0;
      while (p < end && buf[p] >= '0' && buf[p] <= '9') {
        if (exp < 10000) {
          exp = exp * 10 + (buf[p] - '0');
        }
        p++;
      }
      scale += expNegative ? exp : -exp;
    }

    r.pos = p;

    if (!isFloat) {
      // Only reachable with 19+ digits: does not fit the 18-digit fast path.
      return parseLargeInteger(buf, numStart, p);
    }
    if (mantissa == 0) {
      return negative ? -0.0 : 0.0;
    }
    final double d =
        truncated
            ? Double.parseDouble(
                new String(buf, numStart, p - numStart, StandardCharsets.ISO_8859_1))
            : toDouble(-mantissa, scale, negative);
    if (Double.isInfinite(d) || Double.isNaN(d)) {
      throw r.error("JSON number out of range");
    }
    return d;
  }

  /** Integer with 19+ digits: Long when it fits, BigInteger otherwise. */
  private static Number parseLargeInteger(final byte[] buf, final int start, final int end) {
    final String s = new String(buf, start, end - start, StandardCharsets.ISO_8859_1);
    try {
      return Long.parseLong(s);
    } catch (NumberFormatException e) {
      return new BigInteger(s);
    }
  }

  // YAML Flexible Number Parser (0x, 0b, 0o, '_', scientific)
  public static Number tryParseNumber(final byte[] buf, final int start, final int end) {
    if (start >= end) return null;

    int i = start;
    boolean negative = false;
    final byte first = buf[i];

    if (first == '-') {
      negative = true;
      i++;
    } else if (first == '+') {
      i++;
    }

    if (i >= end) return null;

    if (buf[i] == '0' && i + 1 < end) {
      final byte prefix = (byte) (buf[i + 1] | 0x20);
      if (prefix == 'x') return parseRadix(buf, i + 2, end, negative, 4);
      if (prefix == 'b') return parseRadix(buf, i + 2, end, negative, 1);
      if (prefix == 'o') return parseRadix(buf, i + 2, end, negative, 3);
    }

    return parseYamlDecimal(buf, i, end, negative);
  }

  private static Number parseYamlDecimal(
      final byte[] buf, final int start, final int end, final boolean negative) {
    if (buf[start] == '_' || buf[end - 1] == '_') return null;

    final int lastDigit = negative ? 8 : 7; // Long.MIN_VALUE ends in 8, Long.MAX_VALUE in 7
    long negMantissa = 0;
    int i = start;

    int d2;
    while (i + 1 < end && (d2 = digit2(buf, i)) != -1) {
      // negMantissa * 100 - d2 must not underflow Long.MIN_VALUE
      if (negMantissa > Long.MIN_VALUE / 100
          || (negMantissa == Long.MIN_VALUE / 100 && d2 <= lastDigit)) {
        negMantissa = negMantissa * 100 - d2;
        i += 2;
      } else {
        break;
      }
    }

    boolean hasDigits = (i > start);
    boolean hasDot = false;
    boolean mantissaFull = false;
    int scale = 0;

    for (; i < end; i++) {
      final byte b = buf[i];
      if (b == '_') continue;

      final int digit = b - '0';
      if (digit >= 0 && digit <= 9) {
        hasDigits = true;
        if (!mantissaFull) {
          if (negMantissa > Long.MIN_VALUE / 10
              || (negMantissa == Long.MIN_VALUE / 10 && digit <= lastDigit)) {
            negMantissa = negMantissa * 10 - digit;
            if (hasDot) scale++;
          } else {
            mantissaFull = true;
            if (!hasDot) scale--;
          }
        } else if (!hasDot) {
          scale--;
        }
      } else if (b == '.') {
        if (hasDot) return null;
        hasDot = true;
      } else if (b == 'e' || b == 'E') {
        if (!hasDigits) return null;
        return parseYamlScientific(
            buf, start, i + 1, end, negMantissa, scale, hasDot, mantissaFull, negative);
      } else {
        return null;
      }
    }

    if (!hasDigits) return null;

    if (mantissaFull) {
      // Digits were dropped: the mantissa is inexact, so go through the original text.
      return hasDot
          ? (Number) slowDouble(buf, start, end, negative)
          : slowInteger(buf, start, end, negative);
    }
    if (hasDot) {
      return toDouble(negMantissa, scale, negative);
    }
    return toCompactInteger(negMantissa, negative);
  }

  private static Number parseYamlScientific(
      final byte[] buf,
      final int numStart,
      int i,
      final int end,
      final long negMantissa,
      final int baseScale,
      final boolean hasDot,
      final boolean mantissaFull,
      final boolean negative) {

    if (i >= end) return null;

    boolean expNegative = false;
    if (buf[i] == '-') {
      expNegative = true;
      i++;
    } else if (buf[i] == '+') {
      i++;
    }

    if (i >= end || buf[i] == '_' || buf[end - 1] == '_') return null;

    int exp = 0;
    boolean hasExpDigits = false;

    for (; i < end; i++) {
      final byte b = buf[i];
      if (b == '_') continue;
      final int digit = b - '0';
      if (digit >= 0 && digit <= 9) {
        hasExpDigits = true;
        exp = exp * 10 + digit;
        if (exp > 1024) return null;
      } else {
        return null;
      }
    }

    if (!hasExpDigits) return null;

    if (!hasDot && !mantissaFull && !expNegative && exp <= 18) {
      final long multiplier = LONG_POW10[exp];
      if (negMantissa >= Long.MIN_VALUE / multiplier) {
        return toCompactInteger(negMantissa * multiplier, negative);
      }
    }

    if (mantissaFull) {
      return slowDouble(buf, numStart, end, negative);
    }

    final int totalScale = baseScale + (expNegative ? exp : -exp);
    return toDouble(negMantissa, totalScale, negative);
  }

  /** Parses the original text (digits, '.', 'e', sign; '_' removed) with the JDK parser. */
  private static double slowDouble(
      final byte[] buf, final int start, final int end, final boolean negative) {
    final StringBuilder sb = new StringBuilder(end - start + 1);
    if (negative) sb.append('-');
    for (int i = start; i < end; i++) {
      if (buf[i] != '_') sb.append((char) buf[i]);
    }
    return Double.parseDouble(sb.toString());
  }

  /** Integer that does not fit in a long (digits and '_' only). */
  private static Number slowInteger(
      final byte[] buf, final int start, final int end, final boolean negative) {
    final StringBuilder sb = new StringBuilder(end - start + 1);
    if (negative) sb.append('-');
    for (int i = start; i < end; i++) {
      if (buf[i] != '_') sb.append((char) buf[i]);
    }
    return new BigInteger(sb.toString());
  }

  /**
   * Parses 0x / 0b / 0o digits. {@code shift} is bits per digit (4, 1, 3). Returns Integer or Long
   * when the value fits a signed long, BigInteger when it does not, null when malformed.
   */
  private static Number parseRadix(
      final byte[] buf, final int start, final int end, final boolean negative, final int shift) {
    if (start >= end || buf[start] == '_' || buf[end - 1] == '_') return null;
    final int radix = 1 << shift;
    long value = 0;
    boolean overflow = false;
    boolean hasDigits = false;
    for (int i = start; i < end; i++) {
      final byte b = buf[i];
      if (b == '_') continue;
      final int digit;
      if (shift == 4) {
        digit = (b >= 0) ? HEX_DIGITS[b] : -1;
      } else {
        final int d = b - '0';
        digit = (d >= 0 && d < radix) ? d : -1;
      }
      if (digit < 0) return null;
      hasDigits = true;
      // The top `shift` bits must be zero, otherwise the shift would lose information
      if ((value >>> (64 - shift)) != 0) overflow = true;
      value = (value << shift) | digit;
    }
    if (!hasDigits) return null;

    // Fits a signed long: value in [0, 2^63-1], or exactly 2^63 when negative
    if (!overflow && (value >= 0 || (negative && value == Long.MIN_VALUE))) {
      return toCompactInteger(-value, negative);
    }

    final StringBuilder sb = new StringBuilder(end - start + 1);
    for (int i = start; i < end; i++) {
      if (buf[i] != '_') sb.append((char) buf[i]);
    }
    final BigInteger big = new BigInteger(sb.toString(), radix);
    return negative ? big.negate() : big;
  }
}
