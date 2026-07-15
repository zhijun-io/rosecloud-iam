package io.rosecloud.iam.tenancy;

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

  private final TenantRepository tenantRepository;
  private final InvitationRepository invitationRepository;
  private final OutboxAppendService outboxAppendService;
  private final AuditService auditService;
  private final RosecloudIamProperties properties;
  private final Sha256Hasher sha256Hasher;
  private final Clock clock;
  private final SecureRandom secureRandom = new SecureRandom();

  public TenantInvitationService(
      TenantRepository tenantRepository,
      InvitationRepository invitationRepository,
      OutboxAppendService outboxAppendService,
      AuditService auditService,
      RosecloudIamProperties properties,
      Sha256Hasher sha256Hasher) {
    this.tenantRepository = tenantRepository;
    this.invitationRepository = invitationRepository;
    this.outboxAppendService = outboxAppendService;
    this.auditService = auditService;
    this.properties = properties;
    this.sha256Hasher = sha256Hasher;
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

  private String randomToken() {
    byte[] bytes = new byte[32];
    secureRandom.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  private String invitationPayload(
      UUID invitationId, UUID tenantId, String ownerEmail, String token) {
    return "{\"invitationId\":\""
        + invitationId
        + "\",\"tenantId\":\""
        + tenantId
        + "\",\"ownerEmail\":\""
        + escapeJson(ownerEmail)
        + "\",\"token\":\""
        + escapeJson(token)
        + "\"}";
  }

  private String escapeJson(String value) {
    return value.replace("\\", "\\\\").replace("\"", "\\\"");
  }

  public record CreateTenantResult(UUID tenantId, UUID invitationId, String status) {}
}
