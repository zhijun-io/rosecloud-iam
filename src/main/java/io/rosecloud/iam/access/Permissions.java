package io.rosecloud.iam.access;

/** Thin-slice Permission catalog. Codes are stable; modules own descriptions. */
public final class Permissions {

  public static final Permission DEMO_READ =
      new Permission("demo:read", "Read demo protected resource", "demo");
  public static final Permission DEMO_ADMIN =
      new Permission("demo:admin", "Admin demo protected resource", "demo");
  public static final Permission TENANT_INVITE =
      new Permission("tenant:invite", "Invite members into the tenant", "tenancy");
  public static final Permission TENANT_MEMBERSHIP_SUSPEND =
      new Permission(
          "tenant:membership:suspend", "Suspend a membership in the tenant", "tenancy");
  public static final Permission TENANT_OWNER_TRANSFER =
      new Permission("tenant:owner:transfer", "Transfer tenant ownership", "tenancy");

  private Permissions() {}
}
