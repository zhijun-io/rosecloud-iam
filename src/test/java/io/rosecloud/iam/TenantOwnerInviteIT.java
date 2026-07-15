package io.rosecloud.iam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.eatthepath.otp.TimeBasedOneTimePasswordGenerator;
import io.rosecloud.iam.operator.OperatorSetupCliRunner;
import io.rosecloud.iam.shared.Base32Encoding;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

class TenantOwnerInviteIT extends AbstractPostgresIntegrationTest {

  @Autowired MockMvc mockMvc;
  @Autowired JdbcTemplate jdbcTemplate;
  @Autowired OperatorSetupCliRunner operatorSetupCliRunner;

  @BeforeEach
  void cleanDatabase() {
    jdbcTemplate.update("delete from membership");
    jdbcTemplate.update("delete from invitation");
    jdbcTemplate.update("delete from outbox_message");
    jdbcTemplate.update("delete from iam_user");
    jdbcTemplate.update("delete from tenant");
    jdbcTemplate.update("delete from login_session");
    jdbcTemplate.update("delete from operator_setup_token");
    jdbcTemplate.update("delete from platform_operator");
    jdbcTemplate.update("delete from audit_event");
  }

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

  private String createTenantAndReadInvitationToken(String accessToken, String ownerEmail)
      throws Exception {
    MvcResult createResult =
        mockMvc
            .perform(
                post("/api/operator/tenants")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "name": "Short Password Tenant",
                          "ownerEmail": "%s"
                        }
                        """
                            .formatted(ownerEmail)))
            .andExpect(status().isCreated())
            .andReturn();

    String tenantId = extractJsonField(createResult.getResponse().getContentAsString(), "tenantId");
    String outboxPayload =
        jdbcTemplate.queryForObject(
            "select payload from outbox_message where aggregate_id = ? and event_type = ?",
            String.class,
            java.util.UUID.fromString(tenantId),
            "tenant.owner_invited");
    return extractJsonField(outboxPayload, "token");
  }

  private String bootstrapOperatorAndLogin() throws Exception {
    String setupToken = issueSetupToken();
    MvcResult beginResult =
        mockMvc
            .perform(
                post("/api/operator/setup/begin")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "setupToken": "%s",
                          "password": "correct horse battery staple"
                        }
                        """
                            .formatted(setupToken)))
            .andExpect(status().isOk())
            .andReturn();

    String totpSecret = extractJsonField(beginResult.getResponse().getContentAsString(), "totpSecret");

    mockMvc
        .perform(
            post("/api/operator/setup/complete")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "setupToken": "%s",
                      "totpCode": "%s"
                    }
                    """
                        .formatted(setupToken, currentTotpCode(totpSecret))))
        .andExpect(status().isNoContent());

    MvcResult loginResult =
        mockMvc
            .perform(
                post("/api/operator/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "password": "correct horse battery staple",
                          "totpCode": "%s"
                        }
                        """
                            .formatted(currentTotpCode(totpSecret))))
            .andExpect(status().isOk())
            .andReturn();

    return extractJsonField(loginResult.getResponse().getContentAsString(), "accessToken");
  }

  private String issueSetupToken() {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    int exitCode = operatorSetupCliRunner.run(new PrintStream(output, true, StandardCharsets.UTF_8));
    assertThat(exitCode).isZero();
    String setupToken = output.toString(StandardCharsets.UTF_8).trim();
    assertThat(setupToken).isNotBlank();
    return setupToken;
  }

  private String currentTotpCode(String base32Secret)
      throws NoSuchAlgorithmException, InvalidKeyException {
    TimeBasedOneTimePasswordGenerator generator =
        new TimeBasedOneTimePasswordGenerator(Duration.ofSeconds(30), 6);
    SecretKeySpec key = new SecretKeySpec(Base32Encoding.decode(base32Secret), "HmacSHA1");
    return String.format("%06d", generator.generateOneTimePassword(key, Instant.now()));
  }

  private String extractJsonField(String body, String fieldName) {
    String marker = "\"" + fieldName + "\":\"";
    int start = body.indexOf(marker);
    assertThat(start).isGreaterThanOrEqualTo(0);
    int valueStart = start + marker.length();
    int valueEnd = body.indexOf('"', valueStart);
    return body.substring(valueStart, valueEnd);
  }
}
