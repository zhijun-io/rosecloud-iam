package io.rosecloud.iam;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

class StaleAccessTokenIT extends AbstractIamApiIntegrationTest {

  @DynamicPropertySource
  static void shortAccessTokenTtl(DynamicPropertyRegistry registry) {
    registry.add("rosecloud.iam.jwt.access-token-ttl", () -> "2s");
  }

  @Test
  void suspendedMembershipKeepsExistingTenantTokenUntilAccessTtlExpires() throws Exception {
    AcceptedOwnerFixture owner =
        acceptOwnerInvitation(
            bootstrapOperatorAndLogin(),
            "Stale Window Co",
            "stale-window@example.com",
            "owner invite password");
    SessionFixture session = loginUser(owner.email(), owner.password(), owner.totpSecret());
    String tenantToken = selectTenantContext(session.accessToken(), owner.membershipId());

    jdbcTemplate.update(
        "update membership set status = 'SUSPENDED' where id = ?", owner.membershipId());

    mockMvc
        .perform(
            get("/api/demo/read").header(HttpHeaders.AUTHORIZATION, "Bearer " + tenantToken))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            post("/api/me/tenant-context")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + session.accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "membershipId": "%s"
                    }
                    """
                        .formatted(owner.membershipId())))
        .andExpect(status().isForbidden());

    Thread.sleep(2500);

    mockMvc
        .perform(
            get("/api/demo/read").header(HttpHeaders.AUTHORIZATION, "Bearer " + tenantToken))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void logoutRevokesRefreshButAccessTokenWorksUntilAccessTtlExpires() throws Exception {
    AcceptedOwnerFixture owner =
        acceptOwnerInvitation(
            bootstrapOperatorAndLogin(),
            "Revoke Stale Co",
            "revoke-stale@example.com",
            "owner invite password");
    SessionFixture session = loginUser(owner.email(), owner.password(), owner.totpSecret());
    String tenantToken = selectTenantContext(session.accessToken(), owner.membershipId());

    mockMvc
        .perform(
            post("/api/sessions/logout")
                .cookie(session.refreshCookie())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + session.accessToken()))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(post("/api/sessions/refresh").cookie(session.refreshCookie()))
        .andExpect(status().isUnauthorized());

    mockMvc
        .perform(
            get("/api/demo/read").header(HttpHeaders.AUTHORIZATION, "Bearer " + tenantToken))
        .andExpect(status().isOk());

    Thread.sleep(2500);

    mockMvc
        .perform(
            get("/api/demo/read").header(HttpHeaders.AUTHORIZATION, "Bearer " + tenantToken))
        .andExpect(status().isUnauthorized());
  }
}
