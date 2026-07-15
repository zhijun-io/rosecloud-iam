package io.rosecloud.iam.api;

import io.rosecloud.iam.access.AuthorizationException;
import io.rosecloud.iam.identity.FactorChallengeException;
import io.rosecloud.iam.identity.GlobalTotpPolicyException;
import io.rosecloud.iam.identity.LoginRateLimitedException;
import io.rosecloud.iam.identity.UserAuthenticationException;
import io.rosecloud.iam.operator.OperatorAuthenticationException;
import io.rosecloud.iam.operator.OperatorSetupRejectedException;
import io.rosecloud.iam.session.SessionException;
import io.rosecloud.iam.tenancy.TenancyException;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MethodArgumentNotValidException;

@RestControllerAdvice
class ApiExceptionHandler {

  @ExceptionHandler(OperatorSetupRejectedException.class)
  ResponseEntity<Map<String, String>> handleSetupRejected(OperatorSetupRejectedException exception) {
    return ResponseEntity.status(exception.status()).body(Map.of("error", exception.getMessage()));
  }

  @ExceptionHandler(OperatorAuthenticationException.class)
  ResponseEntity<Map<String, String>> handleAuthenticationRejected(
      OperatorAuthenticationException exception) {
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
        .body(Map.of("error", exception.getMessage()));
  }

  @ExceptionHandler(UserAuthenticationException.class)
  ResponseEntity<Map<String, String>> handleUserAuthenticationRejected(
      UserAuthenticationException exception) {
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
        .body(Map.of("error", exception.getMessage()));
  }

  @ExceptionHandler(LoginRateLimitedException.class)
  ResponseEntity<Map<String, String>> handleLoginRateLimited(LoginRateLimitedException exception) {
    long retryAfterSeconds = Math.max(1L, exception.retryAfter().toSeconds());
    return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
        .header(HttpHeaders.RETRY_AFTER, Long.toString(retryAfterSeconds))
        .body(Map.of("error", exception.getMessage()));
  }

  @ExceptionHandler(SessionException.class)
  ResponseEntity<Map<String, String>> handleSessionRejected(SessionException exception) {
    return ResponseEntity.status(exception.status()).body(Map.of("error", exception.getMessage()));
  }

  @ExceptionHandler(TenancyException.class)
  ResponseEntity<Map<String, String>> handleTenancyRejected(TenancyException exception) {
    return ResponseEntity.status(exception.status()).body(Map.of("error", exception.getMessage()));
  }

  @ExceptionHandler(AuthorizationException.class)
  ResponseEntity<Map<String, String>> handleAuthorizationRejected(AuthorizationException exception) {
    return ResponseEntity.status(exception.status()).body(Map.of("error", exception.getMessage()));
  }

  @ExceptionHandler(GlobalTotpPolicyException.class)
  ResponseEntity<Map<String, String>> handleGlobalTotpPolicy(GlobalTotpPolicyException exception) {
    return ResponseEntity.status(exception.status()).body(Map.of("error", exception.getMessage()));
  }

  @ExceptionHandler(FactorChallengeException.class)
  ResponseEntity<Map<String, String>> handleFactorChallenge(FactorChallengeException exception) {
    return ResponseEntity.status(exception.status()).body(Map.of("error", exception.getMessage()));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<Map<String, String>> handleValidationError(
      MethodArgumentNotValidException exception) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "validation failed"));
  }
}
