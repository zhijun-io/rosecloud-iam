package io.rosecloud.iam.access;

import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class BuiltinRolePermissions {

  private static final Map<String, List<Permission>> ROLE_PERMISSIONS =
      Map.of(
          "OWNER",
          List.of(
              Permissions.DEMO_READ,
              Permissions.DEMO_ADMIN,
              Permissions.TENANT_INVITE,
              Permissions.TENANT_MEMBERSHIP_SUSPEND,
              Permissions.TENANT_OWNER_TRANSFER),
          "ADMIN",
          List.of(
              Permissions.DEMO_READ,
              Permissions.DEMO_ADMIN,
              Permissions.TENANT_INVITE,
              Permissions.TENANT_MEMBERSHIP_SUSPEND),
          "MEMBER",
          List.of(Permissions.DEMO_READ));

  public List<String> permissionsFor(String roleCode) {
    return ROLE_PERMISSIONS.getOrDefault(roleCode, List.of()).stream()
        .map(Permission::code)
        .toList();
  }

  public boolean isKnownRole(String roleCode) {
    return ROLE_PERMISSIONS.containsKey(roleCode);
  }

  /** Inviteable non-owner roles for tenant member invitations. */
  public boolean isAssignableMemberRole(String roleCode) {
    return "ADMIN".equals(roleCode) || "MEMBER".equals(roleCode);
  }
}
