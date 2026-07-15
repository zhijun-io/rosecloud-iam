package io.rosecloud.iam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
import java.util.List;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

class OperatorBootstrapIT extends AbstractPostgresIntegrationTest {

  @Autowired MockMvc mockMvc;
  @Autowired JdbcTemplate jdbcTemplate;
  @Autowired OperatorSetupCliRunner operatorSetupCliRunner;

  @BeforeEach
  void cleanDatabase() {
    jdbcTemplate.update("delete from login_session");
    jdbcTemplate.update("delete from operator_setup_token");
    jdbcTemplate.update("delete from platform_operator");
    jdbcTemplate.update("delete from audit_event");
  }

  @Test
  void setupAndLoginHappyPathWritesAuditRows() throws Exception {
    String setupToken = issueSetupToken();

    SetupBeginPayload beginResponse =
        beginSetup(setupToken, """
            {
              "setupToken": "%s",
              "password": "correct horse battery staple"
            }
            """.formatted(setupToken));

    String totpSecret = beginResponse.totpSecret();
    String totpCode = currentTotpCode(totpSecret);

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
                        .formatted(setupToken, totpCode)))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(
            post("/api/operator/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "password": "correct horse battery staple",
                      "totpCode": "000000"
                    }
                    """))
        .andExpect(status().isUnauthorized());

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
        .andExpect(jsonPath("$.tokenType").value("Bearer"))
        .andExpect(jsonPath("$.expiresIn").value(300))
        .andExpect(jsonPath("$.accessToken").isString())
        .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("rc_refresh=")))
        .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("HttpOnly")))
        .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("Secure")))
        .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("SameSite=Lax")))
        .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("Path=/api")));

    List<String> eventTypes =
        jdbcTemplate.queryForList(
            "select event_type from audit_event order by created_at asc", String.class);
    assertThat(eventTypes)
        .contains(
            "operator.setup_token_issued",
            "operator.setup_begun",
            "operator.setup_completed",
            "operator.login_failed",
            "operator.login_succeeded");
  }

  @Test
  void secondBareInitFailsAfterActivation() throws Exception {
    String setupToken = issueSetupToken();
    SetupBeginPayload begin =
        beginSetup(
            setupToken,
            """
            {
              "setupToken": "%s",
              "password": "correct horse battery staple"
            }
            """
                .formatted(setupToken));
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
                        .formatted(setupToken, currentTotpCode(begin.totpSecret()))))
        .andExpect(status().isNoContent());

    ByteArrayOutputStream output = new ByteArrayOutputStream();
    int exitCode = operatorSetupCliRunner.run(new PrintStream(output, true, StandardCharsets.UTF_8));

    assertThat(exitCode).isNotZero();
    assertThat(output.toString(StandardCharsets.UTF_8)).isBlank();
    assertThat(
            jdbcTemplate.queryForList(
                "select event_type from audit_event where event_type = 'operator.setup_rejected'",
                String.class))
        .isNotEmpty();
  }

  @Test
  void abandonedSetupCanBeRetriedWithFreshCliToken() throws Exception {
    String abandoned = issueSetupToken();
    beginSetup(
        abandoned,
        """
        {
          "setupToken": "%s",
          "password": "correct horse battery staple"
        }
        """
            .formatted(abandoned));

    String fresh = issueSetupToken();
    SetupBeginPayload begin =
        beginSetup(
            fresh,
            """
            {
              "setupToken": "%s",
              "password": "correct horse battery staple"
            }
            """
                .formatted(fresh));
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
                        .formatted(fresh, currentTotpCode(begin.totpSecret()))))
        .andExpect(status().isNoContent());
  }

  @Test
  void totpIsRequiredForCompletionAndLogin() throws Exception {
    String setupToken = issueSetupToken();
    beginSetup(setupToken, """
        {
          "setupToken": "%s",
          "password": "correct horse battery staple"
        }
        """.formatted(setupToken));

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
        .andExpect(status().isBadRequest());

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
        .andExpect(status().isBadRequest());
  }

  private String issueSetupToken() {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    int exitCode = operatorSetupCliRunner.run(new PrintStream(output, true, StandardCharsets.UTF_8));
    assertThat(exitCode).isZero();
    String setupToken = output.toString(StandardCharsets.UTF_8).trim();
    assertThat(setupToken).isNotBlank();
    return setupToken;
  }

  private SetupBeginPayload beginSetup(String setupToken, String body) throws Exception {
    MvcResult result =
        mockMvc
            .perform(post("/api/operator/setup/begin").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totpSecret").isString())
            .andExpect(jsonPath("$.otpauthUrl").value(org.hamcrest.Matchers.containsString("otpauth://totp/")))
            .andReturn();

    return new SetupBeginPayload(
        extractJsonField(result.getResponse().getContentAsString(), "totpSecret"),
        extractJsonField(result.getResponse().getContentAsString(), "otpauthUrl"));
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

  private record SetupBeginPayload(String totpSecret, String otpauthUrl) {}
}
