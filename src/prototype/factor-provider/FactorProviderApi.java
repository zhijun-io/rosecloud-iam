// PROTOTYPE — portable interface sketch for issue
// https://github.com/zhijun-io/rosecloud-iam/issues/14
// Domain terms: MfaFeature, Factor, FactorBinding, FactorChallenge

package io.rosecloud.iam.prototype.factor;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Kind of second factor. Password is not a Factor. */
record FactorKind(String code) {
  static final FactorKind TOTP = new FactorKind("totp");
}

record PrincipalRef(String type, String id) {
  static PrincipalRef user(String id) {
    return new PrincipalRef("user", id);
  }
}

record FactorBindingId(String value) {}

/** Opaque enroll-in-progress handle (e.g. pending TOTP secret). */
record EnrollmentSession(String id, FactorKind kind, Instant expiresAt) {}

/** Opaque pending FactorChallenge handle. */
record ChallengeSession(String id, FactorBindingId bindingId, FactorKind kind, Instant expiresAt) {}

record FactorBindingView(
    FactorBindingId id, FactorKind kind, String softLabel, Instant boundAt) {}

record EnrollmentBegin(EnrollmentSession session, String displaySecret, String enrollHint) {}

record ChallengeBegin(ChallengeSession session, String challengeHint) {}

/**
 * One pluggable Factor implementation (TOTP today; another kind later).
 *
 * <p>Credential material stays inside the provider; callers only see ids / hints.
 */
interface FactorProvider {
  FactorKind kind();

  EnrollmentBegin beginEnroll(PrincipalRef principal);

  FactorBindingView completeEnroll(
      PrincipalRef principal, EnrollmentSession session, String proof);

  void revoke(PrincipalRef principal, FactorBindingId bindingId);

  List<FactorBindingView> list(PrincipalRef principal);

  ChallengeBegin beginChallenge(PrincipalRef principal, FactorBindingId bindingId);

  boolean verifyChallenge(ChallengeSession session, String proof);
}

/** Platform registration of Factor kinds + MfaFeature gate. */
interface FactorCatalog {
  void register(FactorProvider provider);

  Optional<FactorProvider> provider(FactorKind kind);

  List<FactorKind> registeredKinds();

  boolean mfaFeatureEnabled();

  void setMfaFeatureEnabled(boolean enabled);

  /** Kinds a Principal may newly bind — empty when MfaFeature is off. */
  List<FactorKind> enrollableKinds();

  /** Existing bindings (always listable for revoke); empty challenge surface when feature off. */
  List<FactorBindingView> listBindings(PrincipalRef principal);

  Optional<ChallengeBegin> beginLoginOrStepUpChallenge(
      PrincipalRef principal, FactorBindingId bindingId);
}
