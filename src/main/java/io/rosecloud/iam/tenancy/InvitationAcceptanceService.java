package io.rosecloud.iam.tenancy;

import io.rosecloud.iam.audit.AuditService;
import io.rosecloud.iam.identity.InviteCredentialException;
import io.rosecloud.iam.identity.InviteCredentialService;
import io.rosecloud.iam.identity.LoginDecision;
import io.rosecloud.iam.shared.Sha256Hasher;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InvitationAcceptanceService {

  private static final String GENERIC_REJECTION = InviteCredentialService.GENERIC_REJECTION;

  private final InvitationRepository invitationRepository;
  private final TenantRepository tenantRepository;
  private final MembershipRepository membershipRepository;
  private final InviteCredentialService inviteCredentialService;
  private final Sha256Hasher sha256Hasher;
  private final AuditService auditService;
  private final Clock clock;

  public InvitationAcceptanceService(
      InvitationRepository invitationRepository,
      TenantRepository tenantRepository,
      MembershipRepository membershipRepository,
      InviteCredentialService inviteCredentialService,
      Sha256Hasher sha256Hasher,
      AuditService auditService) {
    this.invitationRepository = invitationRepository;
    this.tenantRepository = tenantRepository;
    this.membershipRepository = membershipRepository;
    this.inviteCredentialService = inviteCredentialService;
    this.sha256Hasher = sha256Hasher;
    this.auditService = auditService;
    this.clock = Clock.systemUTC();
  }

  @Transactional
  public BeginAcceptanceResult begin(String token, String password) {
    Invitation invitation = requireUsableInvitation(token);
    try {
      InviteCredentialService.AcceptBegin began =
          inviteCredentialService.beginAccept(invitation.email(), password);
      return new BeginAcceptanceResult(began.totpSecret(), began.otpauthUrl());
    } catch (InviteCredentialException exception) {
      throw new TenancyException(HttpStatus.UNAUTHORIZED, exception.getMessage());
    }
  }

  @Transactional
  public void complete(String token, String totpCode) {
    Invitation invitation = requireUsableInvitation(token);
    Tenant tenant = requireTenant(invitation);
    try {
      UUID userId = inviteCredentialService.activatePending(invitation.email());
      acceptInvitation(invitation, tenant, userId, true);
    } catch (InviteCredentialException exception) {
      throw new TenancyException(HttpStatus.UNAUTHORIZED, exception.getMessage());
    }
  }

  @Transactional
  public void joinExisting(String token, String password, String totpCode) {
    Invitation invitation = requireUsableInvitation(token);
    Tenant tenant = requireTenant(invitation);
    try {
      LoginDecision decision =
          inviteCredentialService.authenticateActiveUser(invitation.email(), password);
      if (!(decision instanceof LoginDecision.SessionReady ready)) {
        throw new TenancyException(
            HttpStatus.UNAUTHORIZED, "factor challenge required; complete login first");
      }
      acceptInvitation(invitation, tenant, ready.principalId(), false);
    } catch (InviteCredentialException exception) {
      throw new TenancyException(HttpStatus.UNAUTHORIZED, exception.getMessage());
    }
  }

  private Invitation requireUsableInvitation(String token) {
    Instant now = Instant.now(clock);
    return invitationRepository
        .findByTokenHash(sha256Hasher.hash(token))
        .filter(invitation -> !invitation.isExpired(now))
        .filter(invitation -> !invitation.isAccepted())
        .orElseThrow(() -> new TenancyException(HttpStatus.UNAUTHORIZED, GENERIC_REJECTION));
  }

  private Tenant requireTenant(Invitation invitation) {
    return tenantRepository
        .findById(invitation.tenantId())
        .orElseThrow(() -> new IllegalStateException("Invitation tenant missing"));
  }

  private void acceptInvitation(
      Invitation invitation, Tenant tenant, UUID userId, boolean activateUser) {
    invitation.markAccepted();
    if (tenant.status() == TenantStatus.PENDING) {
      tenant.activate();
    }
    membershipRepository.save(
        new Membership(tenant.id(), userId, invitation.roleCode(), MembershipStatus.ACTIVE));
    auditService.append(
        AuditService.TENANT_INVITATION_ACCEPTED,
        userId,
        "accepted invitation into tenant " + tenant.id() + " as " + invitation.roleCode());
    if (activateUser) {
      auditService.append(
          "tenant.owner_activated", userId, "owner accepted invitation; tenant active");
    }
  }

  public record BeginAcceptanceResult(String totpSecret, String otpauthUrl) {}
}
