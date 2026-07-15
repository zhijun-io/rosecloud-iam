package io.rosecloud.iam.identity;

import org.springframework.http.HttpStatus;

public class GlobalTotpPolicyException extends RuntimeException {

  private final HttpStatus status;

  public GlobalTotpPolicyException(HttpStatus status, String message) {
    super(message);
    this.status = status;
  }

  public HttpStatus status() {
    return status;
  }
}
