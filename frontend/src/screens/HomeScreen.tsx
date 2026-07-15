import type { TokenContext } from "../auth/tokenStore";
import styles from "./ConsoleScreen.module.css";

export type View =
  | "home"
  | "operator-setup"
  | "operator-login"
  | "create-tenant"
  | "accept-invite"
  | "user-login"
  | "select-tenant"
  | "invite-member"
  | "demo";

interface HomeScreenProps {
  currentContext: TokenContext | null;
  activeTenantName: string | null;
  onNavigate: (view: View) => void;
}

const cards: Array<{ view: View; title: string; copy: string }> = [
  {
    view: "operator-setup",
    title: "Operator setup",
    copy: "Paste the one-time setup token from the CLI, enroll TOTP, then activate the platform operator.",
  },
  {
    view: "operator-login",
    title: "Operator login",
    copy: "Sign in as the operator and keep the access token only in memory.",
  },
  {
    view: "create-tenant",
    title: "Create tenant",
    copy: "Create a pending tenant plus owner invitation, then paste the invite token into the acceptance flow.",
  },
  {
    view: "accept-invite",
    title: "Accept invite",
    copy: "Handle both new-user enrollment and the existing-user join path with the same pasted invite token.",
  },
  {
    view: "user-login",
    title: "User login",
    copy: "Login with email, password, and TOTP to get the user-context access token plus refresh cookie.",
  },
  {
    view: "select-tenant",
    title: "Select tenant",
    copy: "Load memberships and mint a tenant-context access token for the rest of the thin slice.",
  },
  {
    view: "invite-member",
    title: "Invite member",
    copy: "Invite another ADMIN or MEMBER while running inside a tenant context.",
  },
  {
    view: "demo",
    title: "Demo permissions",
    copy: "Hit demo:read and demo:admin to prove the expected 200 and 403 edges.",
  },
];

export function HomeScreen({
  currentContext,
  activeTenantName,
  onNavigate,
}: HomeScreenProps) {
  return (
    <div className={styles.stack}>
      <section className={`surface ${styles.hero}`}>
        <p className="eyebrow">Thin Slice I5</p>
        <h2>Minimal React console</h2>
        <p className={styles.lead}>
          This SPA stays intentionally small: no router, no large UI kit, no
          token persistence, and all API calls flow through the generated
          OpenAPI types under <span className="mono">frontend/src/generated/openapi.ts</span>.
        </p>
        <div className={styles.resultGrid}>
          <article className="surface surfaceInset">
            <h3 className={styles.cardTitle}>Current access context</h3>
            <p className={styles.small}>
              {currentContext
                ? `${currentContext} token active in memory`
                : "No access token loaded yet"}
            </p>
          </article>
          <article className="surface surfaceInset">
            <h3 className={styles.cardTitle}>Active tenant</h3>
            <p className={styles.small}>
              {activeTenantName ?? "None selected yet"}
            </p>
          </article>
        </div>
      </section>

      <section className={`surface ${styles.stack}`}>
        <h2>Console flows</h2>
        <div className={styles.cardGrid}>
          {cards.map((card) => (
            <article key={card.view} className="surface surfaceInset">
              <h3 className={styles.cardTitle}>{card.title}</h3>
              <p className={styles.small}>{card.copy}</p>
              <button type="button" onClick={() => onNavigate(card.view)}>
                Open
              </button>
            </article>
          ))}
        </div>
      </section>

      <section className={`surface ${styles.stack}`}>
        <h2>Notes for local demo</h2>
        <ul className={styles.list}>
          <li>
            Start with operator flows, then clear state before user flows because
            operator and user sessions both reuse the <span className="mono">rc_refresh</span> cookie name.
          </li>
          <li>
            Dev traffic stays same-origin in Vite: the app is served from{" "}
            <span className="mono">/</span> and API calls go through{" "}
            <span className="mono">/api</span>.
          </li>
          <li>
            The happy-path smoke to prove manually is Operator → Tenant → Owner
            → Member, ending with demo endpoints returning the expected 200 and
            403 statuses.
          </li>
        </ul>
      </section>
    </div>
  );
}
