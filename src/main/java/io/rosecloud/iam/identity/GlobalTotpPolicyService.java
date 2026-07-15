package io.rosecloud.iam.identity;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Global TOTP belongs to identity. Tenant-scoped actors must not reset another user's MFA.
 */
@Service
public class GlobalTotpPolicyService {

  public void rejectTenantScopedReset(UUID tenantId, UUID targetUserId) {
    throw new GlobalTotpPolicyException(
        HttpStatus.FORBIDDEN, "tenant context cannot reset global TOTP");
  }
}
