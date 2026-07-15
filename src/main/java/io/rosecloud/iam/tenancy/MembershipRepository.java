package io.rosecloud.iam.tenancy;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MembershipRepository extends JpaRepository<Membership, UUID> {}
