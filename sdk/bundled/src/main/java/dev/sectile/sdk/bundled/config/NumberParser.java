package dev.sectile.sdk.bundled.config;

public final class NumberParser {

  private static final double[] POW10 = new double[309];
  private static final long[] LONG_POW10 = new long[19];
  public static final byte[] HEX_DIGITS = new byte[128];

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
   * Zero-allocation IEEE 754 double conversion from negative-accumulated mantissa and decimal
   * scale. scale = fractionalDigits - exponent
   */
  public static double toDouble(final long negMantissa, final int scale, final boolean negative) {
    if (negMantissa == 0) {
      return negative ? -0.0 : 0.0;
    }
    double v = negative ? (double) negMantissa : -(double) negMantissa;
    if (scale == 0) {
      return v;
    }
    if (scale < 0) {
      final int exp = -scale;
      if (exp > 308) {
        return negative ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
      }
      return v * POW10[exp];
    } else {
      if (scale > 308) {
        // Handle subnormal range (1e-309 .. 1e-324) in two steps without String allocation
        final int rem = scale - 308;
        if (rem >= POW10.length) {
          return negative ? -0.0 : 0.0;
        }
        return (v / POW10[308]) / POW10[rem];
      }
      return v / POW10[scale];
    }
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

  // JSON Strict Number Parser (RFC 8259) - Zero String Allocation
  public static Number readJsonNumber(final ByteReader r) {
    final byte[] buf = r.buf;
    final int end = r.end;
    int p = r.pos;

    boolean negative = false;
    byte b = buf[p];
    if (b == '-') {
      negative = true;
      if (++p >= end) throw r.error("número JSON incompleto");
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
      throw r.error("número JSON inválido");
    }

    final int intStart = p;
    long mantissa = firstDigit;
    p++;

    if (firstDigit == 0) {
      if (p < end && buf[p] >= '0' && buf[p] <= '9') {
        r.pos = p;
        throw r.error("no se permiten ceros a la izquierda en JSON");
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

    return readJsonFloatOrBigInt(r, buf, p, end, mantissa, p - intStart, negative);
  }

  private static Number readJsonFloatOrBigInt(
      final ByteReader r,
      final byte[] buf,
      int p,
      final int end,
      long mantissa,
      int digitsRead,
      final boolean negative) {

    boolean isFloat = false;
    int scale = 0;

    // Rare case: integer part has > 18 digits
    while (p < end && buf[p] >= '0' && buf[p] <= '9') {
      final int d = buf[p++] - '0';
      if (digitsRead < 18) {
        mantissa = mantissa * 10 + d;
        digitsRead++;
      } else if (digitsRead == 18) {
        // Check 19th digit for exact Long.MIN_VALUE / Long.MAX_VALUE
        if (Long.compareUnsigned(
                    mantissa * 10 + d, negative ? 0x8000000000000000L : 0x7FFFFFFFFFFFFFFFL)
                <= 0
            && mantissa <= 922337203685477580L) {
          mantissa = mantissa * 10 + d;
          digitsRead++;
        } else {
          isFloat = true;
          scale--;
        }
      } else {
        isFloat = true;
        scale--;
      }
    }

    // Fractional part
    if (p < end && buf[p] == '.') {
      isFloat = true;
      p++;
      if (p >= end || buf[p] < '0' || buf[p] > '9') {
        r.pos = p;
        throw r.error("se esperaba al menos un dígito después de '.'");
      }
      // Fast unchecked 2-digit loop while total significant digits <= 18
      int safeFracEnd = Math.min(end - 1, p + (17 - digitsRead));
      int d2;
      while (p < safeFracEnd && (d2 = digit2(buf, p)) != -1) {
        mantissa = mantissa * 100 + d2;
        p += 2;
        scale += 2;
        digitsRead += 2;
      }
      while (p < end && buf[p] >= '0' && buf[p] <= '9') {
        final int d = buf[p++] - '0';
        if (digitsRead < 18) {
          mantissa = mantissa * 10 + d;
          scale++;
          digitsRead++;
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
        throw r.error("exponente inválido en número JSON");
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
      return negative ? -mantissa : mantissa;
    }
    final double d = toDoublePositive(mantissa, scale, negative);
    if (Double.isInfinite(d) || Double.isNaN(d)) {
      throw r.error("número JSON fuera de rango");
    }
    return d;
  }

  public static double toDoublePositive(
      final long mantissa, final int scale, final boolean negative) {
    if (mantissa == 0) {
      return negative ? -0.0 : 0.0;
    }
    final double v = negative ? -(double) mantissa : (double) mantissa;
    if (scale == 0) {
      return v;
    }
    if (scale < 0) {
      final int exp = -scale;
      if (exp > 308) {
        return negative ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
      }
      return v * POW10[exp];
    } else {
      if (scale > 308) {
        final int rem = scale - 308;
        if (rem >= POW10.length) {
          return negative ? -0.0 : 0.0;
        }
        return (v / POW10[308]) / POW10[rem];
      }
      return v / POW10[scale];
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
      if (prefix == 'x') return parseHex(buf, i + 2, end, negative);
      if (prefix == 'b') return parseBinary(buf, i + 2, end, negative);
      if (prefix == 'o') return parseOctal(buf, i + 2, end, negative);
    }

    return parseYamlDecimal(buf, i, end, negative);
  }

  private static Number parseYamlDecimal(
      final byte[] buf, final int start, final int end, final boolean negative) {
    if (buf[start] == '_' || buf[end - 1] == '_') return null;

    long negMantissa = 0;
    int i = start;

    int d2;
    while (i + 1 < end && (d2 = digit2(buf, i)) != -1) {
      if (negMantissa >= Long.MIN_VALUE / 100) {
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
              || (negMantissa == Long.MIN_VALUE / 10 && digit <= (negative ? 8 : 7))) {
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
            buf, i + 1, end, negMantissa, scale, hasDot, mantissaFull, negative);
      } else {
        return null;
      }
    }

    if (!hasDigits) return null;

    if (hasDot || mantissaFull) {
      return toDouble(negMantissa, scale, negative);
    }
    return toCompactInteger(negMantissa, negative);
  }

  private static Number parseYamlScientific(
      final byte[] buf,
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

    final int totalScale = baseScale + (expNegative ? exp : -exp);
    return toDouble(negMantissa, totalScale, negative);
  }

  private static Number parseHex(
      final byte[] buf, final int start, final int end, final boolean negative) {
    if (start >= end || buf[start] == '_' || buf[end - 1] == '_') return null;
    long value = 0;
    boolean hasDigits = false;
    for (int i = start; i < end; i++) {
      final byte b = buf[i];
      if (b == '_') continue;
      final int digit = (b >= 0) ? HEX_DIGITS[b] : -1;
      if (digit < 0) return null;
      hasDigits = true;
      value = (value << 4) | digit;
    }
    return hasDigits ? toCompactInteger(-value, negative) : null;
  }

  private static Number parseBinary(
      final byte[] buf, final int start, final int end, final boolean negative) {
    if (start >= end || buf[start] == '_' || buf[end - 1] == '_') return null;
    long value = 0;
    boolean hasDigits = false;
    for (int i = start; i < end; i++) {
      final byte b = buf[i];
      if (b == '_') continue;
      if (b == '0' || b == '1') {
        hasDigits = true;
        value = (value << 1) | (b - '0');
      } else {
        return null;
      }
    }
    return hasDigits ? toCompactInteger(-value, negative) : null;
  }

  private static Number parseOctal(
      final byte[] buf, final int start, final int end, final boolean negative) {
    if (start >= end || buf[start] == '_' || buf[end - 1] == '_') return null;
    long value = 0;
    boolean hasDigits = false;
    for (int i = start; i < end; i++) {
      final byte b = buf[i];
      if (b == '_') continue;
      final int digit = b - '0';
      if (digit >= 0 && digit <= 7) {
        hasDigits = true;
        value = (value << 3) | digit;
      } else {
        return null;
      }
    }
    return hasDigits ? toCompactInteger(-value, negative) : null;
  }
}
