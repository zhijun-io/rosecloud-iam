package io.rosecloud.iam.session;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface LoginSessionRepository extends JpaRepository<LoginSession, UUID> {}
