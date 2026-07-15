package io.rosecloud.iam.operator;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface OperatorSetupTokenRepository extends JpaRepository<OperatorSetupToken, UUID> {

  boolean existsByCompletedAtIsNullAndExpiresAtAfter(Instant now);

  Optional<OperatorSetupToken> findByTokenHash(String tokenHash);
}
