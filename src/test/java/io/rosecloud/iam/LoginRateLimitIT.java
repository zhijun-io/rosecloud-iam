package io.rosecloud.iam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

class LoginRateLimitIT extends AbstractIamApiIntegrationTest {

  @DynamicPropertySource
  static void rateLimitProps(DynamicPropertyRegistry registry) {
    registry.add("rosecloud.iam.login-rate-limit.max-failures-before-cooldown", () -> "3");
    registry.add("rosecloud.iam.login-rate-limit.initial-cooldown", () -> "400ms");
    registry.add("rosecloud.iam.login-rate-limit.max-cooldown", () -> "2s");
  }

  @Test
  void repeatedFailuresThrottleByEmailAndIpThenRecoverAfterCooldown() throws Exception {
    AcceptedOwnerFixture owner =
        acceptOwnerInvitation(
            bootstrapOperatorAndLogin(),
            "Rate Limit Co",
            "rate-limit@example.com",
            "owner invite password");

    RequestPostProcessor ipA = remoteIp("203.0.113.10");
    for (int i = 0; i < 3; i++) {
      mockMvc
          .perform(
              post("/api/sessions/login")
                  .with(ipA)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(loginBody(owner.email(), "wrong-password", "000000")))
          .andExpect(status().isUnauthorized());
    }

    MvcResult throttled =
        mockMvc
            .perform(
                post("/api/sessions/login")
                    .with(ipA)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(loginBody(owner.email(), "wrong-password", "000000")))
            .andExpect(status().isTooManyRequests())
            .andReturn();
    assertThat(throttled.getResponse().getHeader("Retry-After")).isNotBlank();
    assertThat(throttled.getResponse().getContentAsString()).contains("login temporarily limited");

    // Different IP is tracked independently for the same email.
    mockMvc
        .perform(
            post("/api/sessions/login")
                .with(remoteIp("203.0.113.20"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginBody(owner.email(), "wrong-password", "000000")))
        .andExpect(status().isUnauthorized());

    Thread.sleep(500);

    String totp = currentTotpCode(owner.totpSecret());
    mockMvc
        .perform(
            post("/api/sessions/login")
                .with(ipA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginBody(owner.email(), owner.password(), totp)))
        .andExpect(status().isOk());
  }

  private static RequestPostProcessor remoteIp(String ip) {
    return request -> {
      request.setRemoteAddr(ip);
      return request;
    };
  }

  private static String loginBody(String email, String password, String totpCode) {
    return """
        {
          "email": "%s",
          "password": "%s",
          "totpCode": "%s"
        }
        """
        .formatted(email, password, totpCode);
  }
}
