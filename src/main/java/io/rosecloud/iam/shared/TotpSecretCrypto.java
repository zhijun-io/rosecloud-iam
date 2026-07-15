package io.rosecloud.iam.shared;

import io.rosecloud.iam.bootstrap.RosecloudIamProperties;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

@Component
public class TotpSecretCrypto {

  private static final String TRANSFORMATION = "AES/GCM/NoPadding";
  private static final int GCM_TAG_BITS = 128;
  private static final int IV_BYTES = 12;

  private final RosecloudIamProperties properties;
  private final SecureRandom secureRandom = new SecureRandom();

  public TotpSecretCrypto(RosecloudIamProperties properties) {
    this.properties = properties;
  }

  public EncryptedSecret encrypt(byte[] rawSecret) {
    try {
      byte[] iv = new byte[IV_BYTES];
      secureRandom.nextBytes(iv);

      Cipher cipher = Cipher.getInstance(TRANSFORMATION);
      cipher.init(Cipher.ENCRYPT_MODE, secretKey(), new GCMParameterSpec(GCM_TAG_BITS, iv));
      byte[] ciphertext = cipher.doFinal(rawSecret);

      byte[] packed = new byte[iv.length + ciphertext.length];
      System.arraycopy(iv, 0, packed, 0, iv.length);
      System.arraycopy(ciphertext, 0, packed, iv.length, ciphertext.length);

      return new EncryptedSecret(
          properties.crypto().totpKeyId(), Base64.getEncoder().encodeToString(packed));
    } catch (GeneralSecurityException exception) {
      throw new IllegalStateException("Unable to encrypt TOTP secret", exception);
    }
  }

  public byte[] decrypt(String keyId, String ciphertext) {
    if (!properties.crypto().totpKeyId().equals(keyId)) {
      throw new IllegalStateException("Unknown TOTP key id: " + keyId);
    }

    try {
      byte[] packed = Base64.getDecoder().decode(ciphertext);
      byte[] iv = Arrays.copyOfRange(packed, 0, IV_BYTES);
      byte[] encrypted = Arrays.copyOfRange(packed, IV_BYTES, packed.length);

      Cipher cipher = Cipher.getInstance(TRANSFORMATION);
      cipher.init(Cipher.DECRYPT_MODE, secretKey(), new GCMParameterSpec(GCM_TAG_BITS, iv));
      return cipher.doFinal(encrypted);
    } catch (GeneralSecurityException exception) {
      throw new IllegalStateException("Unable to decrypt TOTP secret", exception);
    }
  }

  private SecretKeySpec secretKey() {
    byte[] keyBytes = Base64.getDecoder().decode(properties.crypto().totpKey());
    return new SecretKeySpec(keyBytes, "AES");
  }

  public record EncryptedSecret(String keyId, String ciphertext) {}
}
