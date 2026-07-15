import { useState } from "react";
import type {
  ApiResult,
  MembershipSummaryResponse,
  TenantContextResponse,
} from "../api/client";
import styles from "./ConsoleScreen.module.css";

export interface SelectedTenantState {
  membership: MembershipSummaryResponse;
  context: TenantContextResponse;
}

interface SelectTenantScreenProps {
  memberships: MembershipSummaryResponse[];
  selectedTenant: SelectedTenantState | null;
  onLoadMemberships: () => Promise<ApiResult<MembershipSummaryResponse[]>>;
  onSelectMembership: (
    membership: MembershipSummaryResponse,
  ) => Promise<ApiResult<TenantContextResponse>>;
}

export function SelectTenantScreen({
  memberships,
  selectedTenant,
  onLoadMemberships,
  onSelectMembership,
}: SelectTenantScreenProps) {
  const [message, setMessage] = useState<string | null>(null);
  const [isPending, setIsPending] = useState(false);

  async function handleLoadMemberships() {
    setIsPending(true);
    const result = await onLoadMemberships();
    setIsPending(false);
    setMessage(
      result.ok
        ? `Loaded ${result.data.length} active membership(s).`
        : result.error,
    );
  }

  async function handleSelectMembership(membership: MembershipSummaryResponse) {
    setIsPending(true);
    const result = await onSelectMembership(membership);
    setIsPending(false);
    setMessage(
      result.ok
        ? `Tenant context ready for ${membership.tenantName}.`
        : result.error,
    );
  }

  return (
    <div className={styles.stack}>
      <section className={`surface ${styles.stack}`}>
        <p className="eyebrow">Tenant context</p>
        <h2>Select an active membership</h2>
        <p className={styles.lead}>
          This screen exchanges the user-context token for a tenant-context
          token and keeps the new access token only in memory.
        </p>
        <div className="actions">
          <button type="button" onClick={handleLoadMemberships} disabled={isPending}>
            {isPending ? "Loading..." : "Load memberships"}
          </button>
        </div>
        {message ? <p className="statusText">{message}</p> : null}
      </section>

      <section className={`surface ${styles.stack}`}>
        <h3>Memberships</h3>
        {memberships.length > 0 ? (
          <div className={styles.membershipList}>
            {memberships.map((membership) => {
              const isActive =
                selectedTenant?.membership.membershipId === membership.membershipId;

              return (
                <button
                  key={membership.membershipId}
                  type="button"
                  className={`surface surfaceInset ${styles.membershipButton} ${
                    isActive ? styles.membershipActive : ""
                  }`}
                  onClick={() => handleSelectMembership(membership)}
                  disabled={isPending}
                >
                  <strong>{membership.tenantName}</strong>
                  <span className={styles.small}>
                    {membership.roleCode} · {membership.tenantId}
                  </span>
                  <span className={styles.small}>
                    membership: {membership.membershipId}
                  </span>
                </button>
              );
            })}
          </div>
        ) : (
          <p className={styles.emptyState}>
            No memberships loaded yet. Login as a user, then load memberships.
          </p>
        )}
      </section>

      <section className={`surface ${styles.stack}`}>
        <h3>Current tenant context</h3>
        {selectedTenant ? (
          <>
            <div className={styles.resultGrid}>
              <article className="surface surfaceInset">
                <h4 className={styles.cardTitle}>Tenant</h4>
                <p className={styles.small}>{selectedTenant.membership.tenantName}</p>
              </article>
              <article className="surface surfaceInset">
                <h4 className={styles.cardTitle}>Role</h4>
                <p className={styles.small}>{selectedTenant.membership.roleCode}</p>
              </article>
            </div>
            <div className={styles.pillRow}>
              {selectedTenant.context.permissions.map((permission) => (
                <span key={permission} className={styles.pill}>
                  {permission}
                </span>
              ))}
            </div>
          </>
        ) : (
          <p className={styles.emptyState}>
            Select a membership to mint the tenant-context token.
          </p>
        )}
      </section>
    </div>
  );
}
