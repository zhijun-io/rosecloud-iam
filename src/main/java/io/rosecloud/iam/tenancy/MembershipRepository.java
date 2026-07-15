package io.rosecloud.iam.tenancy;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MembershipRepository extends JpaRepository<Membership, UUID> {

  @Query(
      """
      select m.id as membershipId,
             m.tenantId as tenantId,
             t.name as tenantName,
             m.roleCode as roleCode
        from Membership m
        join Tenant t on t.id = m.tenantId
       where m.userId = :userId
         and m.status = io.rosecloud.iam.tenancy.MembershipStatus.ACTIVE
         and t.status = io.rosecloud.iam.tenancy.TenantStatus.ACTIVE
       order by t.createdAt asc, m.createdAt asc
      """)
  List<ActiveMembershipProjection> findActiveMembershipsByUserId(@Param("userId") UUID userId);

  @Query(
      """
      select m.id as membershipId,
             m.tenantId as tenantId,
             t.name as tenantName,
             m.roleCode as roleCode
        from Membership m
        join Tenant t on t.id = m.tenantId
       where m.id = :membershipId
         and m.userId = :userId
         and m.status = io.rosecloud.iam.tenancy.MembershipStatus.ACTIVE
         and t.status = io.rosecloud.iam.tenancy.TenantStatus.ACTIVE
      """)
  Optional<ActiveMembershipProjection> findActiveMembershipByIdAndUserId(
      @Param("membershipId") UUID membershipId, @Param("userId") UUID userId);

  interface ActiveMembershipProjection {
    UUID getMembershipId();

    UUID getTenantId();

    String getTenantName();

    String getRoleCode();
  }
}
