package io.rosecloud.iam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

class MemberInviteRbacIT extends AbstractIamApiIntegrationTest {

  @Test
  void ownerInvitesMemberAndAdminAndDemoPermissionsApply() throws Exception {
    AcceptedOwnerFixture owner =
        acceptOwnerInvitation(
            bootstrapOperatorAndLogin(), "Acme RBAC", "owner@example.com", "owner invite password");
    String ownerTenantToken = tenantToken(owner);

    AcceptedMemberFixture member =
        acceptNewInvitation(
            owner.tenantId(),
            invite(ownerTenantToken, owner.tenantId(), "member@example.com", "MEMBER"),
            "member@example.com",
            "member invite password");
    AcceptedMemberFixture admin =
        acceptNewInvitation(
            owner.tenantId(),
            invite(ownerTenantToken, owner.tenantId(), "admin@example.com", "ADMIN"),
            "admin@example.com",
            "admin invite password");

    String memberTenantToken = tenantToken(member);
    String adminTenantToken = tenantToken(admin);

    assertDemoAccess(memberTenantToken, 200, 403);
    assertDemoAccess(adminTenantToken, 200, 200);
    assertDemoAccess(ownerTenantToken, 200, 200);

    mockMvc
        .perform(
            post("/api/tenants/{tenantId}/invitations", owner.tenantId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminTenantToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "email": "admin-can-invite@example.com",
                      "roleCode": "MEMBER"
                    }
                    """))
        .andExpect(status().isCreated());
  }

  @Test
  void memberCannotInvite() throws Exception {
    AcceptedOwnerFixture owner =
        acceptOwnerInvitation(
            bootstrapOperatorAndLogin(),
            "Acme Member Deny",
            "owner-deny@example.com",
            "owner invite password");
    String ownerTenantToken = tenantToken(owner);
    AcceptedMemberFixture member =
        acceptNewInvitation(
            owner.tenantId(),
            invite(ownerTenantToken, owner.tenantId(), "member-deny@example.com", "MEMBER"),
            "member-deny@example.com",
            "member invite password");
    String memberTenantToken = tenantToken(member);

    mockMvc
        .perform(
            post("/api/tenants/{tenantId}/invitations", owner.tenantId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + memberTenantToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "email": "blocked@example.com",
                      "roleCode": "MEMBER"
                    }
                    """))
        .andExpect(status().isForbidden());
  }

  @Test
  void removedMembershipRejoinCreatesNewMembershipId() throws Exception {
    AcceptedOwnerFixture owner =
        acceptOwnerInvitation(
            bootstrapOperatorAndLogin(),
            "Acme Rejoin",
            "owner-rejoin@example.com",
            "owner invite password");
    String ownerTenantToken = tenantToken(owner);
    AcceptedMemberFixture member =
        acceptNewInvitation(
            owner.tenantId(),
            invite(ownerTenantToken, owner.tenantId(), "member-rejoin@example.com", "MEMBER"),
            "member-rejoin@example.com",
            "member invite password");

    jdbcTemplate.update("update membership set status = 'REMOVED' where id = ?", member.membershipId());

    String rejoinToken =
        invite(ownerTenantToken, owner.tenantId(), "member-rejoin@example.com", "MEMBER");
    mockMvc
        .perform(
            post("/api/invitations/accept/join")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "token": "%s",
                      "password": "%s"
                    }
                    """
                        .formatted(rejoinToken, member.password())))
        .andExpect(status().isNoContent());

    UUID rejoinedMembershipId =
        latestMembershipIdForEmailAndTenant("member-rejoin@example.com", owner.tenantId());

    assertThat(rejoinedMembershipId).isNotEqualTo(member.membershipId());
    assertThat(
            jdbcTemplate.queryForObject(
                """
                select count(*)
                from membership m
                join iam_user u on u.id = m.user_id
                where u.email = ?
                  and m.tenant_id = ?
                """,
                Integer.class,
                "member-rejoin@example.com",
                owner.tenantId()))
        .isEqualTo(2);
  }

  @Test
  void tenantOwnerCannotResetAnotherUsersGlobalTotp() throws Exception {
    AcceptedOwnerFixture owner =
        acceptOwnerInvitation(
            bootstrapOperatorAndLogin(),
            "Acme TOTP Boundary",
            "owner-totp@example.com",
            "owner invite password");
    String ownerTenantToken = tenantToken(owner);
    AcceptedMemberFixture member =
        acceptNewInvitation(
            owner.tenantId(),
            invite(ownerTenantToken, owner.tenantId(), "member-totp@example.com", "MEMBER"),
            "member-totp@example.com",
            "member invite password");

    mockMvc
        .perform(
            post("/api/tenants/{tenantId}/users/{userId}/totp/reset", owner.tenantId(), member.userId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerTenantToken))
        .andExpect(status().isForbidden());
  }

  @Test
  void inviteAndAcceptanceWriteAuditEvents() throws Exception {
    AcceptedOwnerFixture owner =
        acceptOwnerInvitation(
            bootstrapOperatorAndLogin(),
            "Acme Audit",
            "owner-audit@example.com",
            "owner invite password");
    String ownerTenantToken = tenantToken(owner);
    int acceptedBefore = auditCount("tenant.invitation_accepted");

    acceptNewInvitation(
        owner.tenantId(),
        invite(ownerTenantToken, owner.tenantId(), "member-audit@example.com", "MEMBER"),
        "member-audit@example.com",
        "member invite password");

    assertThat(auditCount("tenant.member_invited")).isEqualTo(1);
    assertThat(auditCount("tenant.invitation_accepted")).isEqualTo(acceptedBefore + 1);
  }

  private String tenantToken(AcceptedOwnerFixture fixture) throws Exception {
    SessionFixture session = loginUser(fixture.email(), fixture.password(), fixture.totpSecret());
    return selectTenantContext(session.accessToken(), fixture.membershipId());
  }

  private String tenantToken(AcceptedMemberFixture fixture) throws Exception {
    SessionFixture session = loginUser(fixture.email(), fixture.password(), fixture.totpSecret());
    return selectTenantContext(session.accessToken(), fixture.membershipId());
  }

  private String invite(String tenantAccessToken, UUID tenantId, String email, String roleCode)
      throws Exception {
    mockMvc
        .perform(
            post("/api/tenants/{tenantId}/invitations", tenantId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tenantAccessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "email": "%s",
                      "roleCode": "%s"
                    }
                    """
                        .formatted(email, roleCode)))
        .andExpect(status().isCreated());
    return extractJsonField(latestOutboxPayload(tenantId, "tenant.member_invited"), "token");
  }

  private AcceptedMemberFixture acceptNewInvitation(
      UUID tenantId, String invitationToken, String email, String password) throws Exception {
    mockMvc
        .perform(
            post("/api/invitations/accept/begin")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "token": "%s",
                      "password": "%s"
                    }
                    """
                        .formatted(invitationToken, password)))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            post("/api/invitations/accept/complete")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "token": "%s"
                    }
                    """
                        .formatted(invitationToken)))
        .andExpect(status().isNoContent());

    return new AcceptedMemberFixture(
        tenantId,
        userIdByEmail(email),
        latestMembershipIdForEmailAndTenant(email, tenantId),
        email,
        password,
        null);
  }

  private void assertDemoAccess(String tenantAccessToken, int readStatus, int adminStatus)
      throws Exception {
    mockMvc
        .perform(get("/api/demo/read").header(HttpHeaders.AUTHORIZATION, "Bearer " + tenantAccessToken))
        .andExpect(status().is(readStatus));
    mockMvc
        .perform(get("/api/demo/admin").header(HttpHeaders.AUTHORIZATION, "Bearer " + tenantAccessToken))
        .andExpect(status().is(adminStatus));
  }

  private record AcceptedMemberFixture(
      UUID tenantId,
      UUID userId,
      UUID membershipId,
      String email,
      String password,
      String totpSecret) {}
}
