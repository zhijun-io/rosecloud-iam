package io.rosecloud.iam.api;

import io.rosecloud.iam.operator.OperatorAuthenticationException;
import io.rosecloud.iam.operator.OperatorSetupRejectedException;
import io.rosecloud.iam.tenancy.TenancyException;
import java.util.Map;
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

  @ExceptionHandler(TenancyException.class)
  ResponseEntity<Map<String, String>> handleTenancyRejected(TenancyException exception) {
    return ResponseEntity.status(exception.status()).body(Map.of("error", exception.getMessage()));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<Map<String, String>> handleValidationError(
      MethodArgumentNotValidException exception) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "validation failed"));
  }
}
