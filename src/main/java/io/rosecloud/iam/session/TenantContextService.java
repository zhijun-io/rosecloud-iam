package io.rosecloud.iam.session;

import io.rosecloud.iam.access.BuiltinRolePermissions;
import io.rosecloud.iam.audit.AuditService;
import io.rosecloud.iam.bootstrap.JwtIssuer;
import io.rosecloud.iam.tenancy.UserMembershipService;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TenantContextService {

  private final UserMembershipService userMembershipService;
  private final BuiltinRolePermissions builtinRolePermissions;
  private final JwtIssuer jwtIssuer;
  private final AuditService auditService;

  public TenantContextService(
      UserMembershipService userMembershipService,
      BuiltinRolePermissions builtinRolePermissions,
      JwtIssuer jwtIssuer,
      AuditService auditService) {
    this.userMembershipService = userMembershipService;
    this.builtinRolePermissions = builtinRolePermissions;
    this.jwtIssuer = jwtIssuer;
    this.auditService = auditService;
  }

  @Transactional
  public TenantContextResult selectTenantContext(UUID userId, UUID membershipId) {
    UserMembershipService.ActiveMembership membership =
        userMembershipService.requireActiveMembership(userId, membershipId);
    List<String> permissions = builtinRolePermissions.permissionsFor(membership.roleCode());
    JwtIssuer.IssuedAccessToken accessToken =
        jwtIssuer.issueTenantToken(
            userId, membership.tenantId(), membership.membershipId(), permissions);
    auditService.append(
        AuditService.USER_TENANT_CONTEXT_SELECTED,
        userId,
        "tenant context selected for membership " + membership.membershipId());
    return new TenantContextResult(accessToken.value(), accessToken.expiresInSeconds(), permissions);
  }

  public record TenantContextResult(String accessToken, long expiresInSeconds, List<String> permissions) {}
}
