package io.rosecloud.iam.access;

/** Declared Permission metadata (architecture §5). Codes are never invented at the admin UI. */
public record Permission(String code, String description, String module) {

  public Permission {
    if (code == null || code.isBlank()) {
      throw new IllegalArgumentException("permission code required");
    }
    if (module == null || module.isBlank()) {
      throw new IllegalArgumentException("permission module required");
    }
  }
}
