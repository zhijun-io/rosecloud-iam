package io.rosecloud.iam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.rosecloud.iam.operator.OperatorMfaResetCliRunner;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

class MfaOptionalIT extends AbstractIamApiIntegrationTest {

  @Autowired OperatorMfaResetCliRunner operatorMfaResetCliRunner;

  @Test
  void mfaFeatureFlipBindChallengeRecoveryAndStepUp() throws Exception {
    String operatorToken = bootstrapOperatorAndLogin();

    mockMvc
        .perform(get("/api/operator/mfa-feature").header("Authorization", "Bearer " + operatorToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.enabled").value(false));

    mockMvc
        .perform(
            put("/api/operator/mfa-feature")
                .header("Authorization", "Bearer " + operatorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "enabled": true,
                      "reason": "enable for integration test"
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.enabled").value(true));

    assertThat(auditCount("mfa.feature_changed")).isEqualTo(1);

    MvcResult beginBind =
        mockMvc
            .perform(
                post("/api/operator/factors/totp/begin")
                    .header("Authorization", "Bearer " + operatorToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totpSecret").isString())
            .andReturn();
    String totpSecret =
        extractJsonField(beginBind.getResponse().getContentAsString(), "totpSecret");

    MvcResult completeBind =
        mockMvc
            .perform(
                post("/api/operator/factors/totp/complete")
                    .header("Authorization", "Bearer " + operatorToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "totpCode": "%s"
                        }
                        """
                            .formatted(currentTotpCode(totpSecret))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.recoveryCodes").isArray())
            .andExpect(jsonPath("$.recoveryCodes.length()").value(8))
            .andReturn();

    String recoveryCode =
        firstArrayString(completeBind.getResponse().getContentAsString(), "recoveryCodes");

    MvcResult challengeLogin =
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
            .andExpect(jsonPath("$.status").value("FACTOR_CHALLENGE_REQUIRED"))
            .andExpect(jsonPath("$.challengeId").isString())
            .andReturn();

    String loginBody = challengeLogin.getResponse().getContentAsString();
    String challengeId = extractJsonField(loginBody, "challengeId");
    String bindingId = firstBindingId(loginBody);

    MvcResult sessionAfterChallenge =
        mockMvc
            .perform(
                post("/api/operator/factor-challenge")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "challengeId": "%s",
                          "bindingId": "%s",
                          "totpCode": "%s"
                        }
                        """
                            .formatted(challengeId, bindingId, currentTotpCode(totpSecret))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").isString())
            .andReturn();
    String liveToken =
        extractJsonField(sessionAfterChallenge.getResponse().getContentAsString(), "accessToken");

    mockMvc
        .perform(
            post("/api/operator/step-up")
                .header("Authorization", "Bearer " + liveToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "password": "correct horse battery staple"
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("FACTOR_CHALLENGE_REQUIRED"));

    MvcResult recoveryLogin =
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
            .andExpect(jsonPath("$.status").value("FACTOR_CHALLENGE_REQUIRED"))
            .andReturn();
    String recoveryChallengeId =
        extractJsonField(recoveryLogin.getResponse().getContentAsString(), "challengeId");

    mockMvc
        .perform(
            post("/api/operator/factor-challenge")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "challengeId": "%s",
                      "recoveryCode": "%s"
                    }
                    """
                        .formatted(recoveryChallengeId, recoveryCode)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accessToken").isString());
  }

  @Test
  void localCliResetsOperatorMfaAndRevokesSessions() throws Exception {
    String operatorToken = bootstrapOperatorAndLogin();
    mockMvc
        .perform(
            put("/api/operator/mfa-feature")
                .header("Authorization", "Bearer " + operatorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "enabled": true,
                      "reason": "cli reset prep"
                    }
                    """))
        .andExpect(status().isOk());

    MvcResult beginBind =
        mockMvc
            .perform(
                post("/api/operator/factors/totp/begin")
                    .header("Authorization", "Bearer " + operatorToken))
            .andExpect(status().isOk())
            .andReturn();
    String totpSecret =
        extractJsonField(beginBind.getResponse().getContentAsString(), "totpSecret");
    mockMvc
        .perform(
            post("/api/operator/factors/totp/complete")
                .header("Authorization", "Bearer " + operatorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "totpCode": "%s"
                    }
                    """
                        .formatted(currentTotpCode(totpSecret))))
        .andExpect(status().isOk());

    UUID operatorId =
        jdbcTemplate.queryForObject("select id from platform_operator limit 1", UUID.class);
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    int exitCode =
        operatorMfaResetCliRunner.run(
            new DefaultApplicationArguments(operatorId.toString(), "lost authenticators"),
            new PrintStream(output, true, StandardCharsets.UTF_8));
    assertThat(exitCode).isZero();
    assertThat(output.toString(StandardCharsets.UTF_8)).contains(operatorId.toString());

    Integer unusedCodes =
        jdbcTemplate.queryForObject(
            "select count(*) from recovery_code where used_at is null", Integer.class);
    assertThat(unusedCodes).isZero();
    Boolean hasCipher =
        jdbcTemplate.queryForObject(
            "select totp_secret_ciphertext is not null from platform_operator where id = ?",
            Boolean.class,
            operatorId);
    assertThat(hasCipher).isFalse();

    List<Integer> openSessions =
        jdbcTemplate.queryForList(
            "select count(*) from login_session where principal_id = ? and revoked_at is null",
            Integer.class,
            operatorId);
    assertThat(openSessions.getFirst()).isZero();

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
        .andExpect(jsonPath("$.accessToken").isString())
        .andExpect(jsonPath("$.status").doesNotExist());
  }

  private static String firstArrayString(String body, String fieldName) {
    String marker = "\"" + fieldName + "\":[\"";
    int start = body.indexOf(marker);
    assertThat(start).isGreaterThanOrEqualTo(0);
    int valueStart = start + marker.length();
    int valueEnd = body.indexOf('"', valueStart);
    return body.substring(valueStart, valueEnd);
  }

  private static String firstBindingId(String body) {
    String marker = "\"bindings\":[{\"id\":\"";
    int start = body.indexOf(marker);
    assertThat(start).isGreaterThanOrEqualTo(0);
    int valueStart = start + marker.length();
    int valueEnd = body.indexOf('"', valueStart);
    return body.substring(valueStart, valueEnd);
  }
}
