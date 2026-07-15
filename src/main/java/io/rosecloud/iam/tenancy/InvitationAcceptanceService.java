package io.rosecloud.iam.tenancy;

import io.rosecloud.iam.audit.AuditService;
import io.rosecloud.iam.identity.IamUser;
import io.rosecloud.iam.identity.IamUserRepository;
import io.rosecloud.iam.identity.TotpService;
import io.rosecloud.iam.identity.UserStatus;
import io.rosecloud.iam.shared.Sha256Hasher;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InvitationAcceptanceService {

  private static final String GENERIC_REJECTION = "invitation cannot be accepted";

  private final InvitationRepository invitationRepository;
  private final TenantRepository tenantRepository;
  private final MembershipRepository membershipRepository;
  private final IamUserRepository iamUserRepository;
  private final PasswordEncoder passwordEncoder;
  private final TotpService totpService;
  private final Sha256Hasher sha256Hasher;
  private final AuditService auditService;
  private final Clock clock;

  public InvitationAcceptanceService(
      InvitationRepository invitationRepository,
      TenantRepository tenantRepository,
      MembershipRepository membershipRepository,
      IamUserRepository iamUserRepository,
      PasswordEncoder passwordEncoder,
      TotpService totpService,
      Sha256Hasher sha256Hasher,
      AuditService auditService) {
    this.invitationRepository = invitationRepository;
    this.tenantRepository = tenantRepository;
    this.membershipRepository = membershipRepository;
    this.iamUserRepository = iamUserRepository;
    this.passwordEncoder = passwordEncoder;
    this.totpService = totpService;
    this.sha256Hasher = sha256Hasher;
    this.auditService = auditService;
    this.clock = Clock.systemUTC();
  }

  @Transactional
  public BeginAcceptanceResult begin(String token, String password) {
    Invitation invitation = requireUsableInvitation(token);
    TotpService.TotpEnrollment enrollment =
        totpService.newEnrollment(invitation.email().toLowerCase(Locale.ROOT));

    IamUser user =
        iamUserRepository
            .findByEmailIgnoreCase(invitation.email())
            .map(existing -> replacePendingEnrollment(existing, password, enrollment))
            .orElseGet(
                () ->
                    new IamUser(
                        invitation.email().toLowerCase(Locale.ROOT),
                        passwordEncoder.encode(password),
                        enrollment.encryptedSecret().ciphertext(),
                        enrollment.encryptedSecret().keyId(),
                        UserStatus.PENDING_TOTP));
    iamUserRepository.save(user);

    return new BeginAcceptanceResult(enrollment.secret(), enrollment.otpauthUrl());
  }

  @Transactional
  public void complete(String token, String totpCode) {
    Invitation invitation = requireUsableInvitation(token);
    Tenant tenant =
        tenantRepository
            .findById(invitation.tenantId())
            .orElseThrow(() -> new IllegalStateException("Invitation tenant missing"));
    IamUser user =
        iamUserRepository
            .findByEmailIgnoreCase(invitation.email())
            .filter(candidate -> candidate.status() == UserStatus.PENDING_TOTP)
            .orElseThrow(
                () -> new TenancyException(HttpStatus.UNAUTHORIZED, GENERIC_REJECTION));

    if (!totpService.verify(user.totpSecretKeyId(), user.totpSecretCiphertext(), totpCode)) {
      throw new TenancyException(HttpStatus.UNAUTHORIZED, "invalid TOTP code");
    }

    user.activate();
    invitation.markAccepted();
    tenant.activate();
    membershipRepository.save(
        new Membership(tenant.id(), user.id(), invitation.roleCode(), MembershipStatus.ACTIVE));
    auditService.append(
        "tenant.owner_activated", user.id(), "owner accepted invitation; tenant active");
  }

  private Invitation requireUsableInvitation(String token) {
    Instant now = Instant.now(clock);
    return invitationRepository
        .findByTokenHash(sha256Hasher.hash(token))
        .filter(invitation -> !invitation.isExpired(now))
        .filter(invitation -> !invitation.isAccepted())
        .orElseThrow(() -> new TenancyException(HttpStatus.UNAUTHORIZED, GENERIC_REJECTION));
  }

  private IamUser replacePendingEnrollment(
      IamUser existing, String password, TotpService.TotpEnrollment enrollment) {
    if (existing.status() != UserStatus.PENDING_TOTP) {
      // Same generic response as bad tokens — do not reveal that the email already exists.
      throw new TenancyException(HttpStatus.UNAUTHORIZED, GENERIC_REJECTION);
    }

    existing.replacePendingEnrollment(
        passwordEncoder.encode(password),
        enrollment.encryptedSecret().ciphertext(),
        enrollment.encryptedSecret().keyId());
    return existing;
  }

  public record BeginAcceptanceResult(String totpSecret, String otpauthUrl) {}
}
