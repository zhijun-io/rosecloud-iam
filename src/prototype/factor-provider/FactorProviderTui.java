// PROTOTYPE TUI shell — throwaway

package io.rosecloud.iam.prototype.factor;

import java.util.List;
import java.util.Scanner;

public final class FactorProviderTui {
  private static final String BOLD = "\u001b[1m";
  private static final String DIM = "\u001b[2m";
  private static final String RESET = "\u001b[0m";

  private final FactorCatalog catalog;
  private final PrincipalRef principal = PrincipalRef.user("alice");
  private EnrollmentSession pendingEnroll;
  private String lastEnrollSecret;
  private ChallengeSession pendingChallenge;
  private String lastMessage = "ready";

  private FactorProviderTui(FactorCatalog catalog) {
    this.catalog = catalog;
  }

  public static void main(String[] args) {
    FactorCatalog catalog = new InMemoryFactorCatalog();
    catalog.register(new FakeTotpFactorProvider());
    catalog.register(new FakeSmsFactorProvider());
    new FactorProviderTui(catalog).loop();
  }

  private void loop() {
    Scanner in = new Scanner(System.in);
    while (true) {
      render();
      System.out.print("> ");
      if (!in.hasNextLine()) {
        return;
      }
      String line = in.nextLine().trim();
      if (line.isEmpty()) {
        continue;
      }
      char cmd = line.charAt(0);
      String rest = line.length() > 1 ? line.substring(1).trim() : "";
      try {
        if (!dispatch(cmd, rest, in)) {
          return;
        }
      } catch (RuntimeException ex) {
        lastMessage = "error: " + ex.getMessage();
      }
    }
  }

  private boolean dispatch(char cmd, String rest, Scanner in) {
    switch (cmd) {
      case 'q' -> {
        return false;
      }
      case 'm' -> {
        catalog.setMfaFeatureEnabled(!catalog.mfaFeatureEnabled());
        lastMessage = "MfaFeature → " + catalog.mfaFeatureEnabled();
      }
      case 'e' -> beginEnroll();
      case 'c' -> completeEnroll(rest, in);
      case 'l' -> lastMessage = "listed " + catalog.listBindings(principal).size() + " binding(s)";
      case 'b' -> beginChallenge(rest, in);
      case 'v' -> verify(rest, in);
      case 'r' -> revoke(rest, in);
      default -> lastMessage = "unknown command";
    }
    return true;
  }

  private void beginEnroll() {
    if (catalog.enrollableKinds().isEmpty()) {
      lastMessage = "enroll blocked — MfaFeature off (or no kinds)";
      return;
    }
    FactorProvider totp =
        catalog.provider(FactorKind.TOTP).orElseThrow();
    EnrollmentBegin begin = totp.beginEnroll(principal);
    pendingEnroll = begin.session();
    lastEnrollSecret = begin.displaySecret();
    lastMessage = "enroll begun secret=" + lastEnrollSecret + " (" + begin.enrollHint() + ")";
  }

  private void completeEnroll(String proof, Scanner in) {
    if (pendingEnroll == null) {
      lastMessage = "no pending enroll — press e first";
      return;
    }
    if (proof.isEmpty()) {
      System.out.print("proof (secret): ");
      proof = in.nextLine().trim();
    }
    FactorBindingView view =
        catalog
            .provider(pendingEnroll.kind())
            .orElseThrow()
            .completeEnroll(principal, pendingEnroll, proof);
    pendingEnroll = null;
    lastEnrollSecret = null;
    lastMessage = "bound " + view.id().value() + " " + view.softLabel();
  }

  private void beginChallenge(String bindingId, Scanner in) {
    if (bindingId.isEmpty()) {
      List<FactorBindingView> list = catalog.listBindings(principal);
      if (list.isEmpty()) {
        lastMessage = "no bindings";
        return;
      }
      System.out.print("binding id: ");
      bindingId = in.nextLine().trim();
    }
    var begin =
        catalog.beginLoginOrStepUpChallenge(principal, new FactorBindingId(bindingId));
    if (begin.isEmpty()) {
      lastMessage = "challenge refused — MfaFeature off or unknown binding";
      return;
    }
    pendingChallenge = begin.get().session();
    lastMessage = "challenge " + pendingChallenge.id() + " — " + begin.get().challengeHint();
  }

  private void verify(String proof, Scanner in) {
    if (pendingChallenge == null) {
      lastMessage = "no pending challenge — press b first";
      return;
    }
    if (proof.isEmpty()) {
      System.out.print("proof: ");
      proof = in.nextLine().trim();
    }
    boolean ok =
        catalog
            .provider(pendingChallenge.kind())
            .orElseThrow()
            .verifyChallenge(pendingChallenge, proof);
    pendingChallenge = null;
    lastMessage = ok ? "FactorChallenge PASSED" : "FactorChallenge FAILED";
  }

  private void revoke(String bindingId, Scanner in) {
    if (bindingId.isEmpty()) {
      System.out.print("binding id: ");
      bindingId = in.nextLine().trim();
    }
    FactorBindingId id = new FactorBindingId(bindingId);
    catalog.listBindings(principal).stream()
        .filter(b -> b.id().equals(id))
        .findFirst()
        .ifPresentOrElse(
            view -> {
              catalog.provider(view.kind()).orElseThrow().revoke(principal, id);
              lastMessage = "revoked " + id.value();
            },
            () -> lastMessage = "unknown binding");
  }

  private void render() {
    System.out.print("\u001b[2J\u001b[H");
    System.out.println(BOLD + "Factor provider prototype" + RESET + " " + DIM + "(throwaway)" + RESET);
    System.out.println();
    System.out.println(BOLD + "catalog" + RESET);
    System.out.println("  MfaFeature: " + catalog.mfaFeatureEnabled());
    System.out.println(
        "  registered: "
            + catalog.registeredKinds().stream().map(FactorKind::code).toList());
    System.out.println(
        "  enrollable: "
            + catalog.enrollableKinds().stream().map(FactorKind::code).toList());
    System.out.println();
    System.out.println(BOLD + "principal" + RESET + " " + principal.type() + "/" + principal.id());
    List<FactorBindingView> bindings = catalog.listBindings(principal);
    if (bindings.isEmpty()) {
      System.out.println("  bindings: (none)");
    } else {
      System.out.println("  bindings:");
      for (FactorBindingView b : bindings) {
        System.out.println(
            "    "
                + b.id().value()
                + "  "
                + b.softLabel()
                + "  "
                + DIM
                + b.boundAt()
                + RESET);
      }
    }
    System.out.println();
    System.out.println(BOLD + "pending" + RESET);
    System.out.println(
        "  enroll: "
            + (pendingEnroll == null
                ? "(none)"
                : pendingEnroll.id() + " secret=" + lastEnrollSecret));
    System.out.println(
        "  challenge: " + (pendingChallenge == null ? "(none)" : pendingChallenge.id()));
    System.out.println();
    System.out.println(BOLD + "last" + RESET + " " + lastMessage);
    System.out.println();
    System.out.println(
        BOLD
            + "[m]"
            + RESET
            + DIM
            + " toggle MfaFeature  "
            + RESET
            + BOLD
            + "[e]"
            + RESET
            + DIM
            + " begin enroll  "
            + RESET
            + BOLD
            + "[c]"
            + RESET
            + DIM
            + " complete enroll [proof]  "
            + RESET);
    System.out.println(
        BOLD
            + "[l]"
            + RESET
            + DIM
            + " list  "
            + RESET
            + BOLD
            + "[b]"
            + RESET
            + DIM
            + " begin challenge [bindingId]  "
            + RESET
            + BOLD
            + "[v]"
            + RESET
            + DIM
            + " verify [proof]  "
            + RESET
            + BOLD
            + "[r]"
            + RESET
            + DIM
            + " revoke [bindingId]  "
            + RESET
            + BOLD
            + "[q]"
            + RESET
            + DIM
            + " quit"
            + RESET);
  }
}
