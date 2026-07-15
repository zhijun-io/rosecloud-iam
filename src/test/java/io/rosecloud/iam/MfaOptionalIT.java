package io.rosecloud.iam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

class MfaOptionalIT extends AbstractIamApiIntegrationTest {

  @Test
  void mfaFeatureFlipEnrollChallengeAndRecovery() throws Exception {
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

    MvcResult beginEnroll =
        mockMvc
            .perform(
                post("/api/operator/factors/totp/begin")
                    .header("Authorization", "Bearer " + operatorToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totpSecret").isString())
            .andReturn();
    String totpSecret =
        extractJsonField(beginEnroll.getResponse().getContentAsString(), "totpSecret");

    MvcResult completeEnroll =
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

    String recoveryCode = firstArrayString(completeEnroll.getResponse().getContentAsString(), "recoveryCodes");

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

    String challengeId =
        extractJsonField(challengeLogin.getResponse().getContentAsString(), "challengeId");
    String bindingId =
        jdbcTemplate.queryForObject("select id::text from platform_operator limit 1", String.class);

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
        .andExpect(jsonPath("$.accessToken").isString());

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

  private static String firstArrayString(String body, String fieldName) {
    String marker = "\"" + fieldName + "\":[\"";
    int start = body.indexOf(marker);
    assertThat(start).isGreaterThanOrEqualTo(0);
    int valueStart = start + marker.length();
    int valueEnd = body.indexOf('"', valueStart);
    return body.substring(valueStart, valueEnd);
  }
}
