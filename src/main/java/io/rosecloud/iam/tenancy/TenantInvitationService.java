package io.rosecloud.iam.tenancy;

import io.rosecloud.iam.access.BuiltinRolePermissions;
import io.rosecloud.iam.audit.AuditService;
import io.rosecloud.iam.bootstrap.RosecloudIamProperties;
import io.rosecloud.iam.delivery.OutboxAppendService;
import io.rosecloud.iam.shared.Sha256Hasher;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TenantInvitationService {

  static final String OWNER_ROLE = "OWNER";
  static final String OWNER_INVITED_EVENT = "tenant.owner_invited";
  static final String MEMBER_INVITED_EVENT = "tenant.member_invited";

  private final TenantRepository tenantRepository;
  private final InvitationRepository invitationRepository;
  private final OutboxAppendService outboxAppendService;
  private final AuditService auditService;
  private final RosecloudIamProperties properties;
  private final Sha256Hasher sha256Hasher;
  private final BuiltinRolePermissions builtinRolePermissions;
  private final Clock clock;
  private final SecureRandom secureRandom = new SecureRandom();

  public TenantInvitationService(
      TenantRepository tenantRepository,
      InvitationRepository invitationRepository,
      OutboxAppendService outboxAppendService,
      AuditService auditService,
      RosecloudIamProperties properties,
      Sha256Hasher sha256Hasher,
      BuiltinRolePermissions builtinRolePermissions) {
    this.tenantRepository = tenantRepository;
    this.invitationRepository = invitationRepository;
    this.outboxAppendService = outboxAppendService;
    this.auditService = auditService;
    this.properties = properties;
    this.sha256Hasher = sha256Hasher;
    this.builtinRolePermissions = builtinRolePermissions;
    this.clock = Clock.systemUTC();
  }

  @Transactional
  public CreateTenantResult createTenant(UUID operatorId, String name, String ownerEmail) {
    if (operatorId == null) {
      throw new TenancyException(HttpStatus.UNAUTHORIZED, "operator authentication required");
    }

    String normalizedOwnerEmail = ownerEmail.trim().toLowerCase(Locale.ROOT);
    Tenant tenant = tenantRepository.save(new Tenant(name.trim(), TenantStatus.PENDING));
    String token = randomToken();
    Invitation invitation =
        invitationRepository.save(
            new Invitation(
                tenant.id(),
                normalizedOwnerEmail,
                OWNER_ROLE,
                sha256Hasher.hash(token),
                Instant.now(clock).plus(properties.invitationTokenTtl())));

    outboxAppendService.append(
        "tenant",
        tenant.id(),
        OWNER_INVITED_EVENT,
        invitationPayload(invitation.id(), tenant.id(), normalizedOwnerEmail, token));
    auditService.append(
        "tenant.created", operatorId, "pending tenant created with owner invitation");

    return new CreateTenantResult(tenant.id(), invitation.id(), tenant.status().name());
  }

  @Transactional
  public CreateMemberInvitationResult createMemberInvitation(
      UUID actorUserId, UUID tenantId, String email, String roleCode) {
    if (actorUserId == null) {
      throw new TenancyException(HttpStatus.UNAUTHORIZED, "tenant authentication required");
    }

    Tenant tenant =
        tenantRepository
            .findById(tenantId)
            .orElseThrow(() -> new TenancyException(HttpStatus.NOT_FOUND, "tenant not found"));
    String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
    String normalizedRoleCode = normalizeInvitableRoleCode(roleCode);
    String token = randomToken();
    Invitation invitation =
        invitationRepository.save(
            new Invitation(
                tenant.id(),
                normalizedEmail,
                normalizedRoleCode,
                sha256Hasher.hash(token),
                Instant.now(clock).plus(properties.invitationTokenTtl())));

    outboxAppendService.append(
        "tenant",
        tenant.id(),
        MEMBER_INVITED_EVENT,
        invitationPayload(
            invitation.id(), tenant.id(), normalizedEmail, normalizedRoleCode, token));
    auditService.append(
        AuditService.TENANT_MEMBER_INVITED,
        actorUserId,
        "invited " + normalizedEmail + " as " + normalizedRoleCode + " into tenant " + tenant.id());
    return new CreateMemberInvitationResult(invitation.id());
  }

  private String randomToken() {
    byte[] bytes = new byte[32];
    secureRandom.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  private String invitationPayload(
      UUID invitationId, UUID tenantId, String email, String roleCode, String token) {
    return "{\"invitationId\":\""
        + invitationId
        + "\",\"tenantId\":\""
        + tenantId
        + "\",\"email\":\""
        + escapeJson(email)
        + "\",\"roleCode\":\""
        + escapeJson(roleCode)
        + "\",\"token\":\""
        + escapeJson(token)
        + "\"}";
  }

  private String invitationPayload(UUID invitationId, UUID tenantId, String ownerEmail, String token) {
    return invitationPayload(invitationId, tenantId, ownerEmail, OWNER_ROLE, token);
  }

  private String normalizeInvitableRoleCode(String roleCode) {
    String normalizedRoleCode = roleCode.trim().toUpperCase(Locale.ROOT);
    if (!builtinRolePermissions.isKnownRole(normalizedRoleCode) || OWNER_ROLE.equals(normalizedRoleCode)) {
      throw new TenancyException(HttpStatus.BAD_REQUEST, "roleCode must be ADMIN or MEMBER");
    }
    return normalizedRoleCode;
  }

  private String escapeJson(String value) {
    return value.replace("\\", "\\\\").replace("\"", "\\\"");
  }

  public record CreateTenantResult(UUID tenantId, UUID invitationId, String status) {}

  public record CreateMemberInvitationResult(UUID invitationId) {}
}
