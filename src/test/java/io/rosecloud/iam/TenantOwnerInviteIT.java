package io.rosecloud.iam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

class TenantOwnerInviteIT extends AbstractIamApiIntegrationTest {

  @Test
  void createAcceptAndActivateTenantOwner() throws Exception {
    String accessToken = bootstrapOperatorAndLogin();

    MvcResult createResult =
        mockMvc
            .perform(
                post("/api/operator/tenants")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "name": "Acme",
                          "ownerEmail": "owner@example.com"
                        }
                        """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("PENDING"))
            .andExpect(jsonPath("$.tenantId").isString())
            .andExpect(jsonPath("$.invitationId").isString())
            .andReturn();

    String createBody = createResult.getResponse().getContentAsString();
    String tenantId = extractJsonField(createBody, "tenantId");
    String invitationId = extractJsonField(createBody, "invitationId");

    assertThat(
            jdbcTemplate.queryForObject(
                "select status from tenant where id = ?",
                String.class,
                java.util.UUID.fromString(tenantId)))
        .isEqualTo("PENDING");

    String outboxPayload =
        jdbcTemplate.queryForObject(
            "select payload from outbox_message where aggregate_id = ? and event_type = ?",
            String.class,
            java.util.UUID.fromString(tenantId),
            "tenant.owner_invited");
    assertThat(outboxPayload).contains(invitationId).contains("owner@example.com");
    String invitationToken = extractJsonField(outboxPayload, "token");

    MvcResult beginResult =
        mockMvc
            .perform(
                post("/api/invitations/accept/begin")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "token": "%s",
                          "password": "owner invitation password"
                        }
                        """
                            .formatted(invitationToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totpSecret").isString())
            .andExpect(jsonPath("$.otpauthUrl").value(org.hamcrest.Matchers.containsString("otpauth://totp/")))
            .andReturn();

    assertThat(
            jdbcTemplate.queryForObject(
                "select status from tenant where id = ?",
                String.class,
                java.util.UUID.fromString(tenantId)))
        .isEqualTo("PENDING");

    String totpSecret = extractJsonField(beginResult.getResponse().getContentAsString(), "totpSecret");

    mockMvc
        .perform(
            post("/api/invitations/accept/complete")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "token": "%s",
                      "totpCode": "%s"
                    }
                    """
                        .formatted(invitationToken, currentTotpCode(totpSecret))))
        .andExpect(status().isNoContent());

    assertThat(
            jdbcTemplate.queryForObject(
                "select status from tenant where id = ?",
                String.class,
                java.util.UUID.fromString(tenantId)))
        .isEqualTo("ACTIVE");
    assertThat(
            jdbcTemplate.queryForObject(
                "select count(*) from iam_user where email = ? and status = 'ACTIVE'",
                Integer.class,
                "owner@example.com"))
        .isEqualTo(1);
    assertThat(
            jdbcTemplate.queryForObject(
                """
                select count(*)
                from membership m
                join tenant t on t.id = m.tenant_id
                join iam_user u on u.id = m.user_id
                where t.id = ?
                  and u.email = ?
                  and m.role_code = 'OWNER'
                  and m.status = 'ACTIVE'
                """,
                Integer.class,
                java.util.UUID.fromString(tenantId),
                "owner@example.com"))
        .isEqualTo(1);
  }

  @Test
  void invalidInvitationTokenReturnsGenericUnauthorized() throws Exception {
    mockMvc
        .perform(
            post("/api/invitations/accept/begin")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "token": "garbage-token",
                      "password": "owner invitation password"
                    }
                    """))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error").value("invitation cannot be accepted"));
  }

  @Test
  void shortPasswordIsRejectedBeforeAcceptanceBegins() throws Exception {
    String accessToken = bootstrapOperatorAndLogin();
    String invitationToken = createTenantAndReadInvitationToken(accessToken, "short@example.com");

    mockMvc
        .perform(
            post("/api/invitations/accept/begin")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "token": "%s",
                      "password": "short"
                    }
                    """
                        .formatted(invitationToken)))
        .andExpect(status().isBadRequest());
  }
}
