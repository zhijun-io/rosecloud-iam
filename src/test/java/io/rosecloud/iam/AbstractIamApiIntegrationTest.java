package io.rosecloud.iam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockCookie;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

abstract class AbstractIamApiIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired MockMvc mockMvc;
  @Autowired JdbcTemplate jdbcTemplate;
  @Autowired OperatorSetupCliRunner operatorSetupCliRunner;

  @BeforeEach
  void cleanDatabase() {
    jdbcTemplate.update("delete from membership");
    jdbcTemplate.update("delete from invitation");
    jdbcTemplate.update("delete from outbox_message");
    jdbcTemplate.update("delete from recovery_code");
    jdbcTemplate.update("delete from factor_challenge");
    jdbcTemplate.update("delete from iam_user");
    jdbcTemplate.update("delete from tenant");
    jdbcTemplate.update("delete from login_session");
    jdbcTemplate.update("delete from operator_setup_token");
    jdbcTemplate.update("delete from platform_operator");
    jdbcTemplate.update("delete from audit_event");
    jdbcTemplate.update(
        "update platform_setting set value_bool = false, updated_at = now() where setting_key = 'mfa_feature'");
  }

  String createTenantAndReadInvitationToken(String accessToken, String ownerEmail) throws Exception {
    MvcResult createResult =
        mockMvc
            .perform(
                post("/api/operator/tenants")
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "name": "Tenant for %s",
                          "ownerEmail": "%s"
                        }
                        """
                            .formatted(ownerEmail, ownerEmail)))
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

  AcceptedOwnerFixture acceptOwnerInvitation(
      String operatorAccessToken, String tenantName, String ownerEmail, String password)
      throws Exception {
    MvcResult createResult =
        mockMvc
            .perform(
                post("/api/operator/tenants")
                    .header("Authorization", "Bearer " + operatorAccessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "name": "%s",
                          "ownerEmail": "%s"
                        }
                        """
                            .formatted(tenantName, ownerEmail)))
            .andExpect(status().isCreated())
            .andReturn();

    String createBody = createResult.getResponse().getContentAsString();
    String tenantId = extractJsonField(createBody, "tenantId");
    String invitationId = extractJsonField(createBody, "invitationId");
    String outboxPayload =
        jdbcTemplate.queryForObject(
            "select payload from outbox_message where aggregate_id = ? and event_type = ?",
            String.class,
            java.util.UUID.fromString(tenantId),
            "tenant.owner_invited");
    String invitationToken = extractJsonField(outboxPayload, "token");

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

    String userId =
        jdbcTemplate.queryForObject(
            "select id::text from iam_user where email = ?",
            String.class,
            ownerEmail.toLowerCase(java.util.Locale.ROOT));
    String membershipId =
        jdbcTemplate.queryForObject(
            """
            select m.id::text
            from membership m
            join iam_user u on u.id = m.user_id
            where u.email = ?
            """,
            String.class,
            ownerEmail.toLowerCase(java.util.Locale.ROOT));

    return new AcceptedOwnerFixture(
        java.util.UUID.fromString(tenantId),
        java.util.UUID.fromString(invitationId),
        invitationToken,
        null,
        java.util.UUID.fromString(userId),
        java.util.UUID.fromString(membershipId),
        password,
        ownerEmail.toLowerCase(java.util.Locale.ROOT));
  }

  String bootstrapOperatorAndLogin() throws Exception {
    String setupToken = issueSetupToken();
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
        .andExpect(status().isOk());

    mockMvc
        .perform(
            post("/api/operator/setup/complete")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "setupToken": "%s"
                    }
                    """
                        .formatted(setupToken)))
        .andExpect(status().isNoContent());

    MvcResult loginResult =
        mockMvc
            .perform(
                post("/api/operator/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "password": "correct horse battery staple"
                        }
                        """))
            .andExpect(status().isOk())
            .andReturn();

    return extractJsonField(loginResult.getResponse().getContentAsString(), "accessToken");
  }

  SessionFixture loginUser(String email, String password, String totpSecret) throws Exception {
    String body =
        totpSecret == null
            ? """
              {
                "email": "%s",
                "password": "%s"
              }
              """
                .formatted(email, password)
            : """
              {
                "email": "%s",
                "password": "%s",
                "totpCode": "%s"
              }
              """
                .formatted(email, password, currentTotpCode(totpSecret));
    MvcResult loginResult =
        mockMvc
            .perform(
                post("/api/sessions/login").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk())
            .andReturn();

    return new SessionFixture(
        extractJsonField(loginResult.getResponse().getContentAsString(), "accessToken"),
        refreshCookie(loginResult));
  }

  String selectTenantContext(String userAccessToken, java.util.UUID membershipId) throws Exception {
    MvcResult tenantContextResult =
        mockMvc
            .perform(
                post("/api/me/tenant-context")
                    .header("Authorization", "Bearer " + userAccessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "membershipId": "%s"
                        }
                        """
                            .formatted(membershipId)))
            .andExpect(status().isOk())
            .andReturn();
    return extractJsonField(tenantContextResult.getResponse().getContentAsString(), "accessToken");
  }

  SessionFixture refresh(MockCookie refreshCookie) throws Exception {
    MvcResult refreshResult =
        mockMvc
            .perform(post("/api/sessions/refresh").cookie(refreshCookie))
            .andExpect(status().isOk())
            .andReturn();
    return new SessionFixture(
        extractJsonField(refreshResult.getResponse().getContentAsString(), "accessToken"),
        refreshCookie(refreshResult));
  }

  MockCookie refreshCookie(MvcResult result) {
    String setCookie = result.getResponse().getHeader("Set-Cookie");
    assertThat(setCookie).isNotBlank().contains("rc_refresh=");
    int equalsIndex = setCookie.indexOf('=');
    int separatorIndex = setCookie.indexOf(';', equalsIndex);
    return new MockCookie("rc_refresh", setCookie.substring(equalsIndex + 1, separatorIndex));
  }

  int auditCount(String eventType) {
    Integer count =
        jdbcTemplate.queryForObject(
            "select count(*) from audit_event where event_type = ?", Integer.class, eventType);
    return count == null ? 0 : count;
  }

  String latestOutboxPayload(java.util.UUID aggregateId, String eventType) {
    return jdbcTemplate.queryForObject(
        """
        select payload
        from outbox_message
        where aggregate_id = ?
          and event_type = ?
        order by created_at desc
        limit 1
        """,
        String.class,
        aggregateId,
        eventType);
  }

  java.util.UUID userIdByEmail(String email) {
    return java.util.UUID.fromString(
        jdbcTemplate.queryForObject(
            "select id::text from iam_user where email = ?",
            String.class,
            email.toLowerCase(java.util.Locale.ROOT)));
  }

  java.util.UUID latestMembershipIdForEmailAndTenant(String email, java.util.UUID tenantId) {
    return java.util.UUID.fromString(
        jdbcTemplate.queryForObject(
            """
            select m.id::text
            from membership m
            join iam_user u on u.id = m.user_id
            where u.email = ?
              and m.tenant_id = ?
            order by m.created_at desc
            limit 1
            """,
            String.class,
            email.toLowerCase(java.util.Locale.ROOT),
            tenantId));
  }

  String issueSetupToken() {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    int exitCode = operatorSetupCliRunner.run(new PrintStream(output, true, StandardCharsets.UTF_8));
    assertThat(exitCode).isZero();
    String setupToken = output.toString(StandardCharsets.UTF_8).trim();
    assertThat(setupToken).isNotBlank();
    return setupToken;
  }

  String currentTotpCode(String base32Secret)
      throws NoSuchAlgorithmException, InvalidKeyException {
    TimeBasedOneTimePasswordGenerator generator =
        new TimeBasedOneTimePasswordGenerator(Duration.ofSeconds(30), 6);
    SecretKeySpec key = new SecretKeySpec(Base32Encoding.decode(base32Secret), "HmacSHA1");
    return String.format("%06d", generator.generateOneTimePassword(key, Instant.now()));
  }

  String extractJsonField(String body, String fieldName) {
    String marker = "\"" + fieldName + "\":\"";
    int start = body.indexOf(marker);
    assertThat(start).isGreaterThanOrEqualTo(0);
    int valueStart = start + marker.length();
    int valueEnd = body.indexOf('"', valueStart);
    return body.substring(valueStart, valueEnd);
  }

  record AcceptedOwnerFixture(
      java.util.UUID tenantId,
      java.util.UUID invitationId,
      String invitationToken,
      String totpSecret,
      java.util.UUID userId,
      java.util.UUID membershipId,
      String password,
      String email) {}

  record SessionFixture(String accessToken, MockCookie refreshCookie) {}
}
