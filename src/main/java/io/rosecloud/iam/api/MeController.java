package io.rosecloud.iam.api;

import io.rosecloud.iam.access.UserPrincipal;
import io.rosecloud.iam.session.TenantContextService;
import io.rosecloud.iam.tenancy.UserMembershipService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me")
class MeController {

  private final UserMembershipService userMembershipService;
  private final TenantContextService tenantContextService;

  MeController(
      UserMembershipService userMembershipService, TenantContextService tenantContextService) {
    this.userMembershipService = userMembershipService;
    this.tenantContextService = tenantContextService;
  }

  @GetMapping("/memberships")
  ResponseEntity<List<MembershipSummaryResponse>> memberships(Authentication authentication) {
    UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
    List<MembershipSummaryResponse> memberships =
        userMembershipService.listActiveMemberships(principal.userId()).stream()
            .map(
                membership ->
                    new MembershipSummaryResponse(
                        membership.membershipId(),
                        membership.tenantId(),
                        membership.tenantName(),
                        membership.roleCode()))
            .toList();
    return ResponseEntity.ok(memberships);
  }

  @PostMapping("/tenant-context")
  ResponseEntity<TenantContextResponse> tenantContext(
      Authentication authentication, @Valid @RequestBody TenantContextRequest request) {
    UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
    TenantContextService.TenantContextResult result =
        tenantContextService.selectTenantContext(
            principal.userId(), principal.sessionId(), request.membershipId());
    return ResponseEntity.ok(
        new TenantContextResponse(
            result.accessToken(), "Bearer", result.expiresInSeconds(), result.permissions()));
  }
}
