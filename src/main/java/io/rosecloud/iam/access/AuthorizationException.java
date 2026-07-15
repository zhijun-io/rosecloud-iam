package io.rosecloud.iam.access;

import org.springframework.http.HttpStatus;

public class AuthorizationException extends RuntimeException {

  private final HttpStatus status;

  public AuthorizationException(HttpStatus status, String message) {
    super(message);
    this.status = status;
  }

  public HttpStatus status() {
    return status;
  }
}
