package io.rosecloud.iam.shared;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class UuidV7 {

  private UuidV7() {}

  public static UUID next() {
    byte[] bytes = new byte[16];
    ThreadLocalRandom.current().nextBytes(bytes);

    long millis = Instant.now().toEpochMilli();
    bytes[0] = (byte) (millis >>> 40);
    bytes[1] = (byte) (millis >>> 32);
    bytes[2] = (byte) (millis >>> 24);
    bytes[3] = (byte) (millis >>> 16);
    bytes[4] = (byte) (millis >>> 8);
    bytes[5] = (byte) millis;
    bytes[6] = (byte) ((bytes[6] & 0x0F) | 0x70);
    bytes[8] = (byte) ((bytes[8] & 0x3F) | 0x80);

    ByteBuffer buffer = ByteBuffer.wrap(bytes);
    return new UUID(buffer.getLong(), buffer.getLong());
  }
}
