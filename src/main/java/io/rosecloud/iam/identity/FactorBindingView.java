package io.rosecloud.iam.identity;

import java.util.UUID;

public record FactorBindingView(String id, String kind, String softLabel) {

  static FactorBindingView totp(UUID principalId, String ciphertextHint) {
    String tail =
        ciphertextHint == null || ciphertextHint.length() < 4
            ? "****"
            : ciphertextHint.substring(ciphertextHint.length() - 4);
    return new FactorBindingView(principalId.toString(), "totp", "TOTP · ···" + tail);
  }
}
