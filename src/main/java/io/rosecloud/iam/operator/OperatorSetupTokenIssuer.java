package io.rosecloud.iam.operator;

import io.rosecloud.iam.audit.AuditService;
import io.rosecloud.iam.bootstrap.RosecloudIamProperties;
import io.rosecloud.iam.shared.Sha256Hasher;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OperatorSetupTokenIssuer {

  private final PlatformOperatorRepository platformOperatorRepository;
  private final OperatorSetupTokenRepository operatorSetupTokenRepository;
  private final Sha256Hasher sha256Hasher;
  private final RosecloudIamProperties properties;
  private final AuditService auditService;
  private final Clock clock;
  private final SecureRandom secureRandom = new SecureRandom();

  public OperatorSetupTokenIssuer(
      PlatformOperatorRepository platformOperatorRepository,
      OperatorSetupTokenRepository operatorSetupTokenRepository,
      Sha256Hasher sha256Hasher,
      RosecloudIamProperties properties,
      AuditService auditService) {
    this.platformOperatorRepository = platformOperatorRepository;
    this.operatorSetupTokenRepository = operatorSetupTokenRepository;
    this.sha256Hasher = sha256Hasher;
    this.properties = properties;
    this.auditService = auditService;
    this.clock = Clock.systemUTC();
  }

  @Transactional
  public String issue() {
    if (platformOperatorRepository.existsByStatus(OperatorStatus.ACTIVE)) {
      auditService.append(AuditService.OPERATOR_SETUP_REJECTED, null, "operator already active");
      throw new OperatorSetupRejectedException("operator already initialized");
    }

    // Incomplete bootstrap is recoverable: clear pending state and mint a fresh token.
    platformOperatorRepository.deleteAll();
    operatorSetupTokenRepository.deleteAll();

    Instant now = Instant.now(clock);
    byte[] rawTokenBytes = new byte[24];
    secureRandom.nextBytes(rawTokenBytes);
    String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(rawTokenBytes);

    operatorSetupTokenRepository.save(
        new OperatorSetupToken(sha256Hasher.hash(rawToken), now.plus(properties.setupTokenTtl())));
    auditService.append(AuditService.OPERATOR_SETUP_TOKEN_ISSUED, null, "setup token issued");
    return rawToken;
  }
}
