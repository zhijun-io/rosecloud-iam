package io.rosecloud.iam.identity;

import io.rosecloud.iam.shared.Sha256Hasher;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RecoveryCodeService {

  private static final int CODE_COUNT = 8;

  private final RecoveryCodeRepository recoveryCodeRepository;
  private final Sha256Hasher sha256Hasher;
  private final SecureRandom secureRandom = new SecureRandom();

  public RecoveryCodeService(
      RecoveryCodeRepository recoveryCodeRepository, Sha256Hasher sha256Hasher) {
    this.recoveryCodeRepository = recoveryCodeRepository;
    this.sha256Hasher = sha256Hasher;
  }

  @Transactional
  public List<String> replaceAll(SessionPrincipalKind kind, UUID principalId) {
    recoveryCodeRepository.deleteByPrincipalTypeAndPrincipalId(kind, principalId);
    List<String> plaintext = new ArrayList<>(CODE_COUNT);
    for (int i = 0; i < CODE_COUNT; i++) {
      String code = randomCode();
      plaintext.add(code);
      recoveryCodeRepository.save(new RecoveryCode(kind, principalId, sha256Hasher.hash(code)));
    }
    return plaintext;
  }

  @Transactional
  public boolean consume(SessionPrincipalKind kind, UUID principalId, String rawCode) {
    String hash = sha256Hasher.hash(rawCode.trim());
    Instant now = Instant.now();
    for (RecoveryCode code :
        recoveryCodeRepository.findByPrincipalTypeAndPrincipalIdAndUsedAtIsNull(kind, principalId)) {
      if (code.codeHash().equals(hash)) {
        code.markUsed(now);
        return true;
      }
    }
    return false;
  }

  @Transactional
  public void revokeAll(SessionPrincipalKind kind, UUID principalId) {
    recoveryCodeRepository.deleteByPrincipalTypeAndPrincipalId(kind, principalId);
  }

  private String randomCode() {
    byte[] bytes = new byte[5];
    secureRandom.nextBytes(bytes);
    return HexFormat.of().formatHex(bytes);
  }
}
