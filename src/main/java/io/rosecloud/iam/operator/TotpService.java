package io.rosecloud.iam.operator;

import com.eatthepath.otp.TimeBasedOneTimePasswordGenerator;
import io.rosecloud.iam.shared.Base32Encoding;
import io.rosecloud.iam.shared.TotpSecretCrypto;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;

@Service
class TotpService {

  private static final String HMAC_SHA1 = "HmacSHA1";
  private static final String ISSUER = "RoseCloud IAM";
  private static final String ACCOUNT = "platform-operator";

  private final TotpSecretCrypto totpSecretCrypto;
  private final TimeBasedOneTimePasswordGenerator generator;
  private final SecureRandom secureRandom = new SecureRandom();

  TotpService(TotpSecretCrypto totpSecretCrypto) {
    this.totpSecretCrypto = totpSecretCrypto;
    this.generator = new TimeBasedOneTimePasswordGenerator(Duration.ofSeconds(30), 6);
  }

  TotpEnrollment newEnrollment() {
    byte[] rawSecret = new byte[20];
    secureRandom.nextBytes(rawSecret);

    String base32Secret = Base32Encoding.encode(rawSecret);
    TotpSecretCrypto.EncryptedSecret encryptedSecret = totpSecretCrypto.encrypt(rawSecret);
    String label =
        URLEncoder.encode(ISSUER + ":" + ACCOUNT, StandardCharsets.UTF_8).replace("+", "%20");
    String issuer = URLEncoder.encode(ISSUER, StandardCharsets.UTF_8).replace("+", "%20");

    String otpauthUrl =
        "otpauth://totp/"
            + label
            + "?secret="
            + base32Secret
            + "&issuer="
            + issuer
            + "&algorithm=SHA1&digits=6&period=30";

    return new TotpEnrollment(base32Secret, otpauthUrl, encryptedSecret);
  }

  boolean verify(String encryptedSecretKeyId, String encryptedSecretCiphertext, String totpCode) {
    byte[] rawSecret = totpSecretCrypto.decrypt(encryptedSecretKeyId, encryptedSecretCiphertext);
    SecretKeySpec secretKey = new SecretKeySpec(rawSecret, HMAC_SHA1);
    Instant now = Instant.now();

    try {
      for (int offset = -1; offset <= 1; offset++) {
        int candidate =
            generator.generateOneTimePassword(secretKey, now.plusSeconds((long) offset * 30));
        if (String.format("%06d", candidate).equals(totpCode)) {
          return true;
        }
      }
      return false;
    } catch (InvalidKeyException exception) {
      throw new IllegalStateException("Unable to verify TOTP code", exception);
    }
  }

  record TotpEnrollment(
      String secret,
      String otpauthUrl,
      TotpSecretCrypto.EncryptedSecret encryptedSecret) {}
}
