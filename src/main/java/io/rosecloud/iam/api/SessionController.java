package io.rosecloud.iam.api;

import io.rosecloud.iam.access.TenantPrincipal;
import io.rosecloud.iam.access.UserPrincipal;
import io.rosecloud.iam.audit.AuditService;
import io.rosecloud.iam.identity.FactorChallengeService;
import io.rosecloud.iam.identity.LoginDecision;
import io.rosecloud.iam.identity.UserLoginService;
import io.rosecloud.iam.session.SessionException;
import io.rosecloud.iam.session.UserLoginResult;
import io.rosecloud.iam.session.UserRefreshResult;
import io.rosecloud.iam.session.UserSessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sessions")
class SessionController {

  private final UserLoginService userLoginService;
  private final UserSessionService userSessionService;
  private final RefreshCookieFactory refreshCookieFactory;
  private final FactorChallengeService factorChallengeService;
  private final AuditService auditService;

  SessionController(
      UserLoginService userLoginService,
      UserSessionService userSessionService,
      RefreshCookieFactory refreshCookieFactory,
      FactorChallengeService factorChallengeService,
      AuditService auditService) {
    this.userLoginService = userLoginService;
    this.userSessionService = userSessionService;
    this.refreshCookieFactory = refreshCookieFactory;
    this.factorChallengeService = factorChallengeService;
    this.auditService = auditService;
  }

  @PostMapping("/login")
  ResponseEntity<?> login(
      @Valid @RequestBody UserLoginRequest request, HttpServletRequest httpRequest) {
    LoginDecision decision =
        userLoginService.login(request.email(), request.password(), httpRequest.getRemoteAddr());
    if (decision instanceof LoginDecision.ChallengeRequired challenge) {
      return ResponseEntity.ok(
          FactorChallengeRequiredResponse.of(challenge.challengeId(), challenge.bindings()));
    }
    LoginDecision.SessionReady ready = (LoginDecision.SessionReady) decision;
    UserLoginResult result = userSessionService.createSession(ready.principalId());
    return ResponseEntity.ok()
        .header(HttpHeaders.SET_COOKIE, refreshCookieFactory.issue(result.refreshToken()).toString())
        .body(new AccessTokenResponse(result.accessToken(), "Bearer", result.expiresInSeconds()));
  }

  @PostMapping("/factor-challenge")
  ResponseEntity<AccessTokenResponse> completeFactorChallenge(
      @Valid @RequestBody FactorChallengeRequest request) {
    UUID userId =
        factorChallengeService.verify(
            request.challengeId(),
            request.bindingId(),
            request.totpCode(),
            request.recoveryCode());
    auditService.append(AuditService.USER_LOGIN_SUCCEEDED, userId, "login succeeded");
    UserLoginResult result = userSessionService.createSession(userId);
    return ResponseEntity.ok()
        .header(HttpHeaders.SET_COOKIE, refreshCookieFactory.issue(result.refreshToken()).toString())
        .body(new AccessTokenResponse(result.accessToken(), "Bearer", result.expiresInSeconds()));
  }

  @PostMapping("/refresh")
  ResponseEntity<AccessTokenResponse> refresh(
      @CookieValue(name = "rc_refresh", required = false) String refreshToken) {
    if (refreshToken == null || refreshToken.isBlank()) {
      throw new SessionException(HttpStatus.UNAUTHORIZED, "refresh token required");
    }

    UserRefreshResult result = userSessionService.refresh(refreshToken);
    return ResponseEntity.ok()
        .header(HttpHeaders.SET_COOKIE, refreshCookieFactory.issue(result.refreshToken()).toString())
        .body(new AccessTokenResponse(result.accessToken(), "Bearer", result.expiresInSeconds()));
  }

  @PostMapping("/logout")
  ResponseEntity<Void> logout(
      @CookieValue(name = "rc_refresh", required = false) String refreshToken,
      Authentication authentication) {
    userSessionService.logout(refreshToken, authenticatedUserId(authentication));
    return ResponseEntity.noContent()
        .header(HttpHeaders.SET_COOKIE, refreshCookieFactory.clear().toString())
        .build();
  }

  private UUID authenticatedUserId(Authentication authentication) {
    if (authentication == null || authentication.getPrincipal() == null) {
      return null;
    }
    Object principal = authentication.getPrincipal();
    if (principal instanceof UserPrincipal userPrincipal) {
      return userPrincipal.userId();
    }
    if (principal instanceof TenantPrincipal tenantPrincipal) {
      return tenantPrincipal.userId();
    }
    return null;
  }
}
