package io.rosecloud.iam.identity;

import java.util.UUID;

public record FactorBindingView(String id, String kind, String softLabel) {

  static FactorBindingView totp(UUID principalId) {
    String compact = principalId.toString().replace("-", "");
    String tail = compact.substring(Math.max(0, compact.length() - 4));
    return new FactorBindingView(principalId.toString(), "totp", "TOTP · ···" + tail);
  }
}
