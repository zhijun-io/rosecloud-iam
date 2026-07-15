package io.rosecloud.iam.access;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component
public class TenantPermissionGuard {

  public TenantPrincipal requirePermission(
      Authentication authentication, UUID tenantId, String permissionCode) {
    if (authentication == null || !(authentication.getPrincipal() instanceof TenantPrincipal principal)) {
      throw new AuthorizationException(HttpStatus.FORBIDDEN, "tenant context required");
    }
    if (tenantId != null && !tenantId.equals(principal.tenantId())) {
      throw new AuthorizationException(HttpStatus.FORBIDDEN, "tenant context mismatch");
    }
    if (!principal.permissions().contains(permissionCode)) {
      throw new AuthorizationException(HttpStatus.FORBIDDEN, "forbidden");
    }
    return principal;
  }

  public TenantPrincipal requirePermission(Authentication authentication, String permissionCode) {
    return requirePermission(authentication, null, permissionCode);
  }
}
