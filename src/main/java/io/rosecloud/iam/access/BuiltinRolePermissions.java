package io.rosecloud.iam.access;

import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class BuiltinRolePermissions {

  private static final Map<String, List<String>> ROLE_PERMISSIONS =
      Map.of(
          "OWNER",
              List.of(
                  "demo:read",
                  "demo:admin",
                  "tenant:invite",
                  "tenant:membership:suspend",
                  "tenant:owner:transfer"),
          "ADMIN",
              List.of(
                  "demo:read",
                  "demo:admin",
                  "tenant:invite",
                  "tenant:membership:suspend"),
          "MEMBER", List.of("demo:read"));

  public List<String> permissionsFor(String roleCode) {
    return ROLE_PERMISSIONS.getOrDefault(roleCode, List.of());
  }

  public boolean isKnownRole(String roleCode) {
    return ROLE_PERMISSIONS.containsKey(roleCode);
  }
}
