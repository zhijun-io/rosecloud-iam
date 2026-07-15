package io.rosecloud.iam.identity;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

public record FactorBindingView(String id, String kind, String softLabel) {

  static FactorBindingView totp(UUID principalId) {
    UUID bindingId = totpBindingId(principalId);
    String compact = bindingId.toString().replace("-", "");
    String tail = compact.substring(compact.length() - 4);
    return new FactorBindingView(bindingId.toString(), "totp", "TOTP · ···" + tail);
  }

  static UUID totpBindingId(UUID principalId) {
    return UUID.nameUUIDFromBytes(
        ("factor-binding:totp:" + principalId).getBytes(StandardCharsets.UTF_8));
  }
}
