package io.rosecloud.iam.operator;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface PlatformOperatorRepository extends JpaRepository<PlatformOperator, UUID> {

  Optional<PlatformOperator> findFirstByOrderByCreatedAtAsc();

  boolean existsByStatus(OperatorStatus status);
}
