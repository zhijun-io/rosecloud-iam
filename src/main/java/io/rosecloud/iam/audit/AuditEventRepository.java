package io.rosecloud.iam.audit;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {}
