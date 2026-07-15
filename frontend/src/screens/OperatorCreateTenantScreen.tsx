import { useState, type FormEvent } from "react";
import type {
  ApiResult,
  CreateTenantRequest,
  CreateTenantResponse,
} from "../api/client";
import styles from "./ConsoleScreen.module.css";

interface OperatorCreateTenantScreenProps {
  result: CreateTenantResponse | null;
  onCreate: (
    body: CreateTenantRequest,
  ) => Promise<ApiResult<CreateTenantResponse>>;
}

export function OperatorCreateTenantScreen({
  result,
  onCreate,
}: OperatorCreateTenantScreenProps) {
  const [name, setName] = useState("Acme Workspace");
  const [ownerEmail, setOwnerEmail] = useState("owner@example.com");
  const [message, setMessage] = useState<string | null>(null);
  const [isPending, setIsPending] = useState(false);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setIsPending(true);
    const response = await onCreate({ name, ownerEmail });
    setIsPending(false);
    setMessage(
      response.ok
        ? "Tenant created. Copy the invite token from outbox/logs, then paste it into Accept invite."
        : response.error,
    );
  }

  return (
    <div className={styles.stack}>
      <section className={`surface ${styles.stack}`}>
        <p className="eyebrow">Operator action</p>
        <h2>Create tenant and owner invite</h2>
        <p className={styles.lead}>
          The backend still emits the actual invite token via the outbox flow,
          so this console shows the identifiers you need and points the user to
          paste the invite token manually into the next step.
        </p>
        <form className="formStack" onSubmit={handleSubmit}>
          <label className="fieldStack">
            <span>Tenant name</span>
            <input
              value={name}
              onChange={(event) => setName(event.target.value)}
              required
            />
          </label>
          <label className="fieldStack">
            <span>Owner email</span>
            <input
              type="email"
              value={ownerEmail}
              onChange={(event) => setOwnerEmail(event.target.value)}
              required
            />
          </label>
          <div className="actions">
            <button type="submit" disabled={isPending}>
              {isPending ? "Creating..." : "Create tenant"}
            </button>
          </div>
        </form>
      </section>

      <section className={`surface ${styles.resultPanel}`}>
        <h3>Created identifiers</h3>
        {result ? (
          <>
            <div className={styles.resultGrid}>
              <article className="surface surfaceInset">
                <h4 className={styles.cardTitle}>Tenant ID</h4>
                <pre className={`${styles.codeBlock} ${styles.inlineCode}`}>
                  {result.tenantId}
                </pre>
              </article>
              <article className="surface surfaceInset">
                <h4 className={styles.cardTitle}>Invitation ID</h4>
                <pre className={`${styles.codeBlock} ${styles.inlineCode}`}>
                  {result.invitationId}
                </pre>
              </article>
            </div>
            <div className={styles.callout}>
              <p className={styles.small}>
                Dev note: fetch the invite token from{" "}
                <span className="mono">outbox_message.payload</span> or local logs,
                then paste it into the Accept invite screen.
              </p>
            </div>
          </>
        ) : (
          <p className={styles.emptyState}>
            No tenant created yet. Login as the operator first.
          </p>
        )}
        {message ? <p className="statusText">{message}</p> : null}
      </section>
    </div>
  );
}
