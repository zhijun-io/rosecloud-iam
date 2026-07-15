package io.rosecloud.iam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nimbusds.jwt.SignedJWT;
import jakarta.servlet.http.Cookie;
import java.text.ParseException;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

class UserSessionIT extends AbstractIamApiIntegrationTest {

  @Test
  void loginListMembershipsAndSelectTenantContext() throws Exception {
    String operatorAccessToken = bootstrapOperatorAndLogin();
    AcceptedOwnerFixture owner =
        acceptOwnerInvitation(
            operatorAccessToken,
            "Acme",
            "owner@example.com",
            "owner invitation password");

    SessionFixture login = loginUser(owner.email(), owner.password(), owner.totpSecret());

    mockMvc
        .perform(
            get("/api/me/memberships")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + login.accessToken()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", Matchers.hasSize(1)))
        .andExpect(jsonPath("$[0].membershipId").value(owner.membershipId().toString()))
        .andExpect(jsonPath("$[0].tenantId").value(owner.tenantId().toString()))
        .andExpect(jsonPath("$[0].tenantName").value("Acme"))
        .andExpect(jsonPath("$[0].roleCode").value("OWNER"));

    MvcResult tenantContextResult =
        mockMvc
            .perform(
                post("/api/me/tenant-context")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + login.accessToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "membershipId": "%s"
                        }
                        """
                            .formatted(owner.membershipId())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.permissions", Matchers.containsInAnyOrder(
                    "demo:read",
                    "demo:admin",
                    "tenant:invite",
                    "tenant:membership:suspend",
                    "tenant:owner:transfer")))
            .andReturn();

    String tenantToken = extractJsonField(tenantContextResult.getResponse().getContentAsString(), "accessToken");
    assertTenantTokenClaims(tenantToken, owner.userId(), owner.tenantId(), owner.membershipId());
    assertThat(auditCount("user.login_succeeded")).isEqualTo(1);
    assertThat(auditCount("user.tenant_context_selected")).isEqualTo(1);
  }

  @Test
  void refreshRotatesAndNewCookieRefreshesAgain() throws Exception {
    AcceptedOwnerFixture owner =
        acceptOwnerInvitation(
            bootstrapOperatorAndLogin(),
            "Acme Refresh",
            "refresh@example.com",
            "owner invitation password");

    SessionFixture login = loginUser(owner.email(), owner.password(), owner.totpSecret());
    SessionFixture refreshed = refresh(login.refreshCookie());

    assertThat(refreshed.refreshCookie().getValue()).isNotEqualTo(login.refreshCookie().getValue());

    SessionFixture refreshedAgain = refresh(refreshed.refreshCookie());
    assertThat(refreshedAgain.refreshCookie().getValue())
        .isNotEqualTo(refreshed.refreshCookie().getValue());
    assertThat(auditCount("user.refresh_succeeded")).isEqualTo(2);
  }

  @Test
  void oldRefreshReuseOutsideGraceRevokesWholeFamily() throws Exception {
    AcceptedOwnerFixture owner =
        acceptOwnerInvitation(
            bootstrapOperatorAndLogin(),
            "Acme Reuse",
            "reuse@example.com",
            "owner invitation password");

    SessionFixture login = loginUser(owner.email(), owner.password(), owner.totpSecret());
    SessionFixture refreshed = refresh(login.refreshCookie());
    Cookie oldCookie = new Cookie("rc_refresh", refreshed.refreshCookie().getValue());
    SessionFixture newest = refresh(refreshed.refreshCookie());

    jdbcTemplate.update(
        "update login_session set rotated_at = now() - interval '1 minute' where principal_id = ?",
        owner.userId());

    mockMvc
        .perform(post("/api/sessions/refresh").cookie(oldCookie))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error").value("invalid refresh token"));

    mockMvc
        .perform(post("/api/sessions/refresh").cookie(newest.refreshCookie()))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error").value("invalid refresh token"));

    assertThat(auditCount("user.refresh_reuse_revoked")).isEqualTo(1);
  }

  @Test
  void previousRefreshReuseWithinGraceReturnsRetryableConflict() throws Exception {
    AcceptedOwnerFixture owner =
        acceptOwnerInvitation(
            bootstrapOperatorAndLogin(),
            "Acme Grace",
            "grace@example.com",
            "owner invitation password");

    SessionFixture login = loginUser(owner.email(), owner.password(), owner.totpSecret());
    refresh(login.refreshCookie());

    mockMvc
        .perform(post("/api/sessions/refresh").cookie(login.refreshCookie()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error").value("refresh_in_progress"));
  }

  @Test
  void logoutThenRefreshFails() throws Exception {
    AcceptedOwnerFixture owner =
        acceptOwnerInvitation(
            bootstrapOperatorAndLogin(),
            "Acme Logout",
            "logout@example.com",
            "owner invitation password");

    SessionFixture login = loginUser(owner.email(), owner.password(), owner.totpSecret());

    mockMvc
        .perform(post("/api/sessions/logout").cookie(login.refreshCookie()))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(post("/api/sessions/refresh").cookie(login.refreshCookie()))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error").value("invalid refresh token"));

    assertThat(auditCount("user.logout")).isEqualTo(1);
  }

  @Test
  void suspendedMembershipCannotBeSelectedForTenantContext() throws Exception {
    AcceptedOwnerFixture owner =
        acceptOwnerInvitation(
            bootstrapOperatorAndLogin(),
            "Acme Suspended",
            "suspended@example.com",
            "owner invitation password");

    SessionFixture login = loginUser(owner.email(), owner.password(), owner.totpSecret());
    jdbcTemplate.update("update membership set status = 'SUSPENDED' where id = ?", owner.membershipId());

    mockMvc
        .perform(
            post("/api/me/tenant-context")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + login.accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "membershipId": "%s"
                    }
                    """
                        .formatted(owner.membershipId())))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error").value("membership is not ACTIVE"));
  }

  @Test
  void loginFailureUsesGenericMessageAndAuditsFailure() throws Exception {
    mockMvc
        .perform(
            post("/api/sessions/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "email": "missing@example.com",
                      "password": "wrong password",
                      "totpCode": "123456"
                    }
                    """))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error").value("invalid user credentials"));

    assertThat(auditCount("user.login_failed")).isEqualTo(1);
  }

  private SessionFixture loginUser(String email, String password, String totpSecret) throws Exception {
    MvcResult loginResult =
        mockMvc
            .perform(
                post("/api/sessions/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "email": "%s",
                          "password": "%s",
                          "totpCode": "%s"
                        }
                        """
                            .formatted(email, password, currentTotpCode(totpSecret))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tokenType").value("Bearer"))
            .andReturn();

    return new SessionFixture(
        extractJsonField(loginResult.getResponse().getContentAsString(), "accessToken"),
        refreshCookie(loginResult));
  }

  private SessionFixture refresh(Cookie refreshCookie) throws Exception {
    MvcResult refreshResult =
        mockMvc
            .perform(post("/api/sessions/refresh").cookie(refreshCookie))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tokenType").value("Bearer"))
            .andReturn();
    return new SessionFixture(
        extractJsonField(refreshResult.getResponse().getContentAsString(), "accessToken"),
        refreshCookie(refreshResult));
  }

  private Cookie refreshCookie(MvcResult result) {
    String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
    assertThat(setCookie).isNotBlank().contains("rc_refresh=");
    int equalsIndex = setCookie.indexOf('=');
    int separatorIndex = setCookie.indexOf(';', equalsIndex);
    return new Cookie("rc_refresh", setCookie.substring(equalsIndex + 1, separatorIndex));
  }

  private int auditCount(String eventType) {
    Integer count =
        jdbcTemplate.queryForObject(
            "select count(*) from audit_event where event_type = ?", Integer.class, eventType);
    return count == null ? 0 : count;
  }

  private void assertTenantTokenClaims(
      String token, UUID userId, UUID tenantId, UUID membershipId) throws ParseException {
    var claims = SignedJWT.parse(token).getJWTClaimsSet();
    assertThat(claims.getStringClaim("typ")).isEqualTo("tenant");
    assertThat(claims.getSubject()).isEqualTo(userId.toString());
    assertThat(claims.getStringClaim("tenant_id")).isEqualTo(tenantId.toString());
    assertThat(claims.getStringClaim("membership_id")).isEqualTo(membershipId.toString());
    assertThat(claims.getStringListClaim("permissions"))
        .containsExactlyInAnyOrder(
            "demo:read",
            "demo:admin",
            "tenant:invite",
            "tenant:membership:suspend",
            "tenant:owner:transfer");
  }

  private record SessionFixture(String accessToken, Cookie refreshCookie) {}
}
