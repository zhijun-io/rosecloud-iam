package io.rosecloud.iam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.rosecloud.iam.operator.OperatorSetupCliRunner;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

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
    jdbcTemplate.update(
        "update platform_setting set value_bool = false, updated_at = now() where setting_key = 'mfa_feature'");
  }

  @Test
  void setupAndLoginHappyPathWritesAuditRows() throws Exception {
    String setupToken = issueSetupToken();

    beginSetup(setupToken);

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

    mockMvc
        .perform(
            post("/api/operator/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "password": "wrong-password-value-here"
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
                      "password": "correct horse battery staple"
                    }
                    """))
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
    beginSetup(setupToken);
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
    beginSetup(abandoned);

    String fresh = issueSetupToken();
    beginSetup(fresh);
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
                        .formatted(fresh)))
        .andExpect(status().isNoContent());
  }

  @Test
  void passwordOnlySetupDoesNotRequireTotp() throws Exception {
    String setupToken = issueSetupToken();
    beginSetup(setupToken);

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
        .andExpect(jsonPath("$.accessToken").isString());
  }

  private String issueSetupToken() {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    int exitCode = operatorSetupCliRunner.run(new PrintStream(output, true, StandardCharsets.UTF_8));
    assertThat(exitCode).isZero();
    String setupToken = output.toString(StandardCharsets.UTF_8).trim();
    assertThat(setupToken).isNotBlank();
    return setupToken;
  }

  private void beginSetup(String setupToken) throws Exception {
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
        .andExpect(jsonPath("$.totpSecret").doesNotExist())
        .andExpect(jsonPath("$.otpauthUrl").doesNotExist());
  }
}
