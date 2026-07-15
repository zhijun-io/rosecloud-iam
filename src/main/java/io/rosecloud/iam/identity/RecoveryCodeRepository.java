package io.rosecloud.iam.identity;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface RecoveryCodeRepository extends JpaRepository<RecoveryCode, UUID> {

  List<RecoveryCode> findByPrincipalTypeAndPrincipalIdAndUsedAtIsNull(
      SessionPrincipalKind principalType, UUID principalId);

  void deleteByPrincipalTypeAndPrincipalId(SessionPrincipalKind principalType, UUID principalId);
}
