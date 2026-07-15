package io.rosecloud.iam.session;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface LoginSessionRepository extends JpaRepository<LoginSession, UUID> {

  Optional<LoginSession> findByRefreshTokenHash(String refreshTokenHash);

  Optional<LoginSession> findByPreviousRefreshTokenHash(String previousRefreshTokenHash);

  List<LoginSession> findByPrincipalTypeAndPrincipalIdAndRevokedAtIsNullOrderByCreatedAtAsc(
      SessionPrincipalType principalType, UUID principalId);

  @Modifying
  @Query(
      """
      update LoginSession session
         set session.revokedAt = :revokedAt
       where session.familyId = :familyId
         and session.revokedAt is null
      """)
  int revokeFamily(@Param("familyId") UUID familyId, @Param("revokedAt") Instant revokedAt);
}
