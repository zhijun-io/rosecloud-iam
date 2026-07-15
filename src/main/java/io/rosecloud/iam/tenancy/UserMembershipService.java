package io.rosecloud.iam.tenancy;

import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserMembershipService {

  private final MembershipRepository membershipRepository;

  public UserMembershipService(MembershipRepository membershipRepository) {
    this.membershipRepository = membershipRepository;
  }

  @Transactional(readOnly = true)
  public List<ActiveMembership> listActiveMemberships(UUID userId) {
    return membershipRepository.findActiveMembershipsByUserId(userId).stream()
        .map(
            membership ->
                new ActiveMembership(
                    membership.getMembershipId(),
                    membership.getTenantId(),
                    membership.getTenantName(),
                    membership.getRoleCode()))
        .toList();
  }

  @Transactional(readOnly = true)
  public ActiveMembership requireActiveMembership(UUID userId, UUID membershipId) {
    return membershipRepository
        .findActiveMembershipByIdAndUserId(membershipId, userId)
        .map(
            membership ->
                new ActiveMembership(
                    membership.getMembershipId(),
                    membership.getTenantId(),
                    membership.getTenantName(),
                    membership.getRoleCode()))
        .orElseThrow(
            () -> new TenancyException(HttpStatus.FORBIDDEN, "membership is not ACTIVE"));
  }

  public record ActiveMembership(
      UUID membershipId, UUID tenantId, String tenantName, String roleCode) {}
}
