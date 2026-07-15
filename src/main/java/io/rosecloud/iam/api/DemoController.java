package io.rosecloud.iam.api;

import io.rosecloud.iam.access.Permissions;
import io.rosecloud.iam.access.TenantPermissionGuard;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/demo")
class DemoController {

  private final TenantPermissionGuard tenantPermissionGuard;

  DemoController(TenantPermissionGuard tenantPermissionGuard) {
    this.tenantPermissionGuard = tenantPermissionGuard;
  }

  @GetMapping("/read")
  ResponseEntity<DemoPermissionResponse> read(Authentication authentication) {
    tenantPermissionGuard.requirePermission(authentication, Permissions.DEMO_READ.code());
    return ResponseEntity.ok(new DemoPermissionResponse(Permissions.DEMO_READ.code()));
  }

  @GetMapping("/admin")
  ResponseEntity<DemoPermissionResponse> admin(Authentication authentication) {
    tenantPermissionGuard.requirePermission(authentication, Permissions.DEMO_ADMIN.code());
    return ResponseEntity.ok(new DemoPermissionResponse(Permissions.DEMO_ADMIN.code()));
  }
}
