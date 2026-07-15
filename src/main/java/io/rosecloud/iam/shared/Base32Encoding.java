package io.rosecloud.iam.shared;

public final class Base32Encoding {

  private static final char[] ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".toCharArray();
  private static final int[] LOOKUP = new int[128];

  static {
    for (int i = 0; i < LOOKUP.length; i++) {
      LOOKUP[i] = -1;
    }
    for (int i = 0; i < ALPHABET.length; i++) {
      LOOKUP[ALPHABET[i]] = i;
    }
  }

  private Base32Encoding() {}

  public static String encode(byte[] value) {
    StringBuilder builder = new StringBuilder((value.length * 8 + 4) / 5);
    int buffer = 0;
    int bitsLeft = 0;

    for (byte next : value) {
      buffer = (buffer << 8) | (next & 0xFF);
      bitsLeft += 8;

      while (bitsLeft >= 5) {
        builder.append(ALPHABET[(buffer >> (bitsLeft - 5)) & 0x1F]);
        bitsLeft -= 5;
      }
    }

    if (bitsLeft > 0) {
      builder.append(ALPHABET[(buffer << (5 - bitsLeft)) & 0x1F]);
    }

    return builder.toString();
  }

  public static byte[] decode(String value) {
    int buffer = 0;
    int bitsLeft = 0;
    byte[] output = new byte[value.length() * 5 / 8];
    int outputIndex = 0;

    for (int i = 0; i < value.length(); i++) {
      char next = Character.toUpperCase(value.charAt(i));
      if (next >= LOOKUP.length || LOOKUP[next] < 0) {
        throw new IllegalArgumentException("Invalid base32 character: " + next);
      }

      buffer = (buffer << 5) | LOOKUP[next];
      bitsLeft += 5;

      if (bitsLeft >= 8) {
        output[outputIndex++] = (byte) ((buffer >> (bitsLeft - 8)) & 0xFF);
        bitsLeft -= 8;
      }
    }

    byte[] exact = new byte[outputIndex];
    System.arraycopy(output, 0, exact, 0, outputIndex);
    return exact;
  }
}
