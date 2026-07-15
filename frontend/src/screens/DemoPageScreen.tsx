import type { ApiResult, DemoPermissionResponse } from "../api/client";
import type { SelectedTenantState } from "./SelectTenantScreen";
import styles from "./ConsoleScreen.module.css";

export interface DemoCallState {
  label: string;
  status: number | null;
  ok: boolean;
  detail: string;
}

interface DemoPageScreenProps {
  selectedTenant: SelectedTenantState | null;
  readResult: DemoCallState | null;
  adminResult: DemoCallState | null;
  onRunRead: () => Promise<ApiResult<DemoPermissionResponse>>;
  onRunAdmin: () => Promise<ApiResult<DemoPermissionResponse>>;
}

export function DemoPageScreen({
  selectedTenant,
  readResult,
  adminResult,
  onRunRead,
  onRunAdmin,
}: DemoPageScreenProps) {
  async function handleReadClick() {
    await onRunRead();
  }

  async function handleAdminClick() {
    await onRunAdmin();
  }

  return (
    <div className={styles.stack}>
      <section className={`surface ${styles.stack}`}>
        <p className="eyebrow">Permission seam</p>
        <h2>Demo endpoints</h2>
        <p className={styles.lead}>
          Use the active tenant-context token to prove the thin-slice RBAC path.
          Owners and admins should see <span className="mono">200</span> for both
          buttons, while members should see <span className="mono">200</span> for{" "}
          <span className="mono">demo:read</span> and{" "}
          <span className="mono">403</span> for{" "}
          <span className="mono">demo:admin</span>.
        </p>
        {selectedTenant ? (
          <p className={styles.small}>
            Active tenant: {selectedTenant.membership.tenantName} (
            {selectedTenant.membership.roleCode})
          </p>
        ) : (
          <p className={styles.emptyState}>
            Select a tenant context first.
          </p>
        )}
        <div className="actions">
          <button type="button" onClick={handleReadClick} disabled={!selectedTenant}>
            Call demo:read
          </button>
          <button
            type="button"
            className="ghostButton"
            onClick={handleAdminClick}
            disabled={!selectedTenant}
          >
            Call demo:admin
          </button>
        </div>
      </section>

      <section className={`surface ${styles.stack}`}>
        <h3>Results</h3>
        <div className={styles.statusList}>
          {[readResult, adminResult]
            .filter((result): result is DemoCallState => result !== null)
            .map((result) => (
              <article key={`${result.label}-${result.status}`} className={styles.statusRow}>
                <div>
                  <strong>{result.label}</strong>
                  <p className={styles.small}>{result.detail}</p>
                </div>
                <span
                  className={`${styles.statusValue} ${
                    result.ok ? styles.statusSuccess : styles.statusError
                  }`}
                >
                  {result.status ?? "n/a"}
                </span>
              </article>
            ))}
        </div>
        {!readResult && !adminResult ? (
          <p className={styles.emptyState}>
            No demo calls made yet.
          </p>
        ) : null}
      </section>
    </div>
  );
}
