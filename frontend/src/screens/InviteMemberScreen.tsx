import { useState, type FormEvent } from "react";
import type {
  ApiResult,
  CreateMemberInvitationRequest,
  CreateMemberInvitationResponse,
} from "../api/client";
import type { SelectedTenantState } from "./SelectTenantScreen";
import styles from "./ConsoleScreen.module.css";

interface InviteMemberScreenProps {
  selectedTenant: SelectedTenantState | null;
  result: CreateMemberInvitationResponse | null;
  onInvite: (
    body: CreateMemberInvitationRequest,
  ) => Promise<ApiResult<CreateMemberInvitationResponse>>;
}

export function InviteMemberScreen({
  selectedTenant,
  result,
  onInvite,
}: InviteMemberScreenProps) {
  const [email, setEmail] = useState("member@example.com");
  const [roleCode, setRoleCode] = useState<"ADMIN" | "MEMBER">("MEMBER");
  const [message, setMessage] = useState<string | null>(null);
  const [isPending, setIsPending] = useState(false);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setIsPending(true);
    const response = await onInvite({ email, roleCode });
    setIsPending(false);
    setMessage(
      response.ok
        ? "Member invite created. Paste the invite token into Accept invite."
        : response.error,
    );
  }

  return (
    <div className={styles.stack}>
      <section className={`surface ${styles.stack}`}>
        <p className="eyebrow">Tenant admin</p>
        <h2>Invite another member</h2>
        <p className={styles.lead}>
          This uses the active tenant-context token. Keep the invite token flow
          manual for now by copying from the outbox or local logs.
        </p>
        {selectedTenant ? (
          <p className={styles.small}>
            Active tenant: {selectedTenant.membership.tenantName} (
            {selectedTenant.membership.roleCode})
          </p>
        ) : (
          <p className={styles.emptyState}>
            Select a tenant context first. Invites require tenant-scoped access.
          </p>
        )}
        <form className="formStack" onSubmit={handleSubmit}>
          <label className="fieldStack">
            <span>Email</span>
            <input
              type="email"
              value={email}
              onChange={(event) => setEmail(event.target.value)}
              required
            />
          </label>
          <label className="fieldStack">
            <span>Role</span>
            <select
              value={roleCode}
              onChange={(event) =>
                setRoleCode(event.target.value as "ADMIN" | "MEMBER")
              }
            >
              <option value="ADMIN">ADMIN</option>
              <option value="MEMBER">MEMBER</option>
            </select>
          </label>
          <div className="actions">
            <button type="submit" disabled={isPending || !selectedTenant}>
              {isPending ? "Inviting..." : "Create invite"}
            </button>
          </div>
        </form>
      </section>

      <section className={`surface ${styles.resultPanel}`}>
        <h3>Invite result</h3>
        {result ? (
          <>
            <pre className={`${styles.codeBlock} ${styles.inlineCode}`}>
              {result.invitationId}
            </pre>
            <p className={styles.small}>
              Next step: paste the invite token into Accept invite and finish the
              new-user or existing-user path there.
            </p>
          </>
        ) : (
          <p className={styles.emptyState}>
            No member invite created yet.
          </p>
        )}
        {message ? <p className="statusText">{message}</p> : null}
      </section>
    </div>
  );
}
