package io.rosecloud.iam.operator;

import org.springframework.http.HttpStatus;

public class OperatorSetupRejectedException extends RuntimeException {

  private final HttpStatus status;

  OperatorSetupRejectedException(String message) {
    this(HttpStatus.CONFLICT, message);
  }

  OperatorSetupRejectedException(HttpStatus status, String message) {
    super(message);
    this.status = status;
  }

  public HttpStatus status() {
    return status;
  }
}
