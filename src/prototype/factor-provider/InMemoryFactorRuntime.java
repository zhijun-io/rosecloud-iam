// PROTOTYPE scaffolding — fake TOTP + catalog. Not production.

package io.rosecloud.iam.prototype.factor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class FakeTotpFactorProvider implements FactorProvider {
  private record StoredBinding(FactorBindingId id, String secret, Instant boundAt) {}

  private record PendingEnroll(EnrollmentSession session, String secret) {}

  private record PendingChallenge(ChallengeSession session, String expectedSecret) {}

  private final Map<String, List<StoredBinding>> byPrincipal = new ConcurrentHashMap<>();
  private final Map<String, PendingEnroll> enrollments = new ConcurrentHashMap<>();
  private final Map<String, PendingChallenge> challenges = new ConcurrentHashMap<>();

  @Override
  public FactorKind kind() {
    return FactorKind.TOTP;
  }

  @Override
  public EnrollmentBegin beginEnroll(PrincipalRef principal) {
    String secret = "proto-" + UUID.randomUUID().toString().substring(0, 8);
    var session =
        new EnrollmentSession("enr-" + UUID.randomUUID(), kind(), Instant.now().plusSeconds(300));
    enrollments.put(session.id(), new PendingEnroll(session, secret));
    return new EnrollmentBegin(
        session,
        secret,
        "PROTOTYPE: complete enroll by submitting proof equal to the secret");
  }

  @Override
  public FactorBindingView completeEnroll(
      PrincipalRef principal, EnrollmentSession session, String proof) {
    PendingEnroll pending = enrollments.remove(session.id());
    if (pending == null || !pending.secret.equals(proof)) {
      throw new IllegalArgumentException("enroll proof failed");
    }
    var id = new FactorBindingId("bind-" + UUID.randomUUID().toString().substring(0, 8));
    var stored = new StoredBinding(id, pending.secret, Instant.now());
    byPrincipal.computeIfAbsent(key(principal), ignored -> new ArrayList<>()).add(stored);
    return view(stored);
  }

  @Override
  public void revoke(PrincipalRef principal, FactorBindingId bindingId) {
    List<StoredBinding> list = byPrincipal.getOrDefault(key(principal), List.of());
    list.removeIf(b -> b.id.equals(bindingId));
  }

  @Override
  public List<FactorBindingView> list(PrincipalRef principal) {
    return byPrincipal.getOrDefault(key(principal), List.of()).stream().map(this::view).toList();
  }

  @Override
  public ChallengeBegin beginChallenge(PrincipalRef principal, FactorBindingId bindingId) {
    StoredBinding binding =
        byPrincipal.getOrDefault(key(principal), List.of()).stream()
            .filter(b -> b.id.equals(bindingId))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("unknown binding"));
    var session =
        new ChallengeSession(
            "chal-" + UUID.randomUUID(), bindingId, kind(), Instant.now().plusSeconds(120));
    challenges.put(session.id(), new PendingChallenge(session, binding.secret));
    return new ChallengeBegin(
        session, "PROTOTYPE: verify by submitting proof equal to binding secret");
  }

  @Override
  public boolean verifyChallenge(ChallengeSession session, String proof) {
    PendingChallenge pending = challenges.remove(session.id());
    return pending != null && pending.expectedSecret.equals(proof);
  }

  private FactorBindingView view(StoredBinding stored) {
    String label = "TOTP · ···" + stored.secret.substring(Math.max(0, stored.secret.length() - 4));
    return new FactorBindingView(stored.id, kind(), label, stored.boundAt);
  }

  private static String key(PrincipalRef principal) {
    return principal.type() + ":" + principal.id();
  }
}

/** Second Factor stub — proves catalog can hold >1 kind without behavior. */
final class FakeSmsFactorProvider implements FactorProvider {
  @Override
  public FactorKind kind() {
    return new FactorKind("sms-stub");
  }

  @Override
  public EnrollmentBegin beginEnroll(PrincipalRef principal) {
    throw new UnsupportedOperationException("sms-stub: not implemented in prototype");
  }

  @Override
  public FactorBindingView completeEnroll(
      PrincipalRef principal, EnrollmentSession session, String proof) {
    throw new UnsupportedOperationException("sms-stub: not implemented in prototype");
  }

  @Override
  public void revoke(PrincipalRef principal, FactorBindingId bindingId) {
    throw new UnsupportedOperationException("sms-stub: not implemented in prototype");
  }

  @Override
  public List<FactorBindingView> list(PrincipalRef principal) {
    return List.of();
  }

  @Override
  public ChallengeBegin beginChallenge(PrincipalRef principal, FactorBindingId bindingId) {
    throw new UnsupportedOperationException("sms-stub: not implemented in prototype");
  }

  @Override
  public boolean verifyChallenge(ChallengeSession session, String proof) {
    return false;
  }
}

final class InMemoryFactorCatalog implements FactorCatalog {
  private final Map<String, FactorProvider> providers = new LinkedHashMap<>();
  private boolean mfaFeatureEnabled;

  @Override
  public void register(FactorProvider provider) {
    providers.put(provider.kind().code(), provider);
  }

  @Override
  public Optional<FactorProvider> provider(FactorKind kind) {
    return Optional.ofNullable(providers.get(kind.code()));
  }

  @Override
  public List<FactorKind> registeredKinds() {
    return providers.values().stream().map(FactorProvider::kind).toList();
  }

  @Override
  public boolean mfaFeatureEnabled() {
    return mfaFeatureEnabled;
  }

  @Override
  public void setMfaFeatureEnabled(boolean enabled) {
    this.mfaFeatureEnabled = enabled;
  }

  @Override
  public List<FactorKind> enrollableKinds() {
    if (!mfaFeatureEnabled) {
      return List.of();
    }
    return registeredKinds();
  }

  @Override
  public List<FactorBindingView> listBindings(PrincipalRef principal) {
    List<FactorBindingView> all = new ArrayList<>();
    for (FactorProvider provider : providers.values()) {
      all.addAll(provider.list(principal));
    }
    return all;
  }

  @Override
  public Optional<ChallengeBegin> beginLoginOrStepUpChallenge(
      PrincipalRef principal, FactorBindingId bindingId) {
    if (!mfaFeatureEnabled) {
      return Optional.empty();
    }
    return listBindings(principal).stream()
        .filter(b -> b.id().equals(bindingId))
        .findFirst()
        .flatMap(
            view ->
                provider(view.kind())
                    .map(p -> p.beginChallenge(principal, bindingId)));
  }
}
