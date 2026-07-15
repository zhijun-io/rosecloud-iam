package io.rosecloud.iam.tenancy;

import org.springframework.http.HttpStatus;

public class TenancyException extends RuntimeException {

  private final HttpStatus status;

  public TenancyException(HttpStatus status, String message) {
    super(message);
    this.status = status;
  }

  public HttpStatus status() {
    return status;
  }
}
