package io.rosecloud.iam.api;

import io.rosecloud.iam.access.OperatorPrincipal;
import io.rosecloud.iam.tenancy.InvitationAcceptanceService;
import io.rosecloud.iam.tenancy.TenantInvitationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
class TenantController {

  private final TenantInvitationService tenantInvitationService;
  private final InvitationAcceptanceService invitationAcceptanceService;

  TenantController(
      TenantInvitationService tenantInvitationService,
      InvitationAcceptanceService invitationAcceptanceService) {
    this.tenantInvitationService = tenantInvitationService;
    this.invitationAcceptanceService = invitationAcceptanceService;
  }

  @PostMapping("/operator/tenants")
  ResponseEntity<CreateTenantResponse> createTenant(
      Authentication authentication, @Valid @RequestBody CreateTenantRequest request) {
    OperatorPrincipal principal = (OperatorPrincipal) authentication.getPrincipal();
    TenantInvitationService.CreateTenantResult result =
        tenantInvitationService.createTenant(
            principal.operatorId(), request.name(), request.ownerEmail());
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(new CreateTenantResponse(result.tenantId(), result.invitationId(), result.status()));
  }

  @PostMapping("/invitations/accept/begin")
  ResponseEntity<InvitationAcceptBeginResponse> beginAcceptance(
      @Valid @RequestBody InvitationAcceptBeginRequest request) {
    InvitationAcceptanceService.BeginAcceptanceResult result =
        invitationAcceptanceService.begin(request.token(), request.password());
    return ResponseEntity.ok(
        new InvitationAcceptBeginResponse(result.totpSecret(), result.otpauthUrl()));
  }

  @PostMapping("/invitations/accept/complete")
  ResponseEntity<Void> completeAcceptance(
      @Valid @RequestBody InvitationAcceptCompleteRequest request) {
    invitationAcceptanceService.complete(request.token(), request.totpCode());
    return ResponseEntity.noContent().build();
  }
}
