import { useState, type FormEvent } from "react";
import type {
  ApiResult,
  OperatorSetupBeginRequest,
  OperatorSetupBeginResponse,
  OperatorSetupCompleteRequest,
} from "../api/client";
import styles from "./ConsoleScreen.module.css";

interface OperatorSetupScreenProps {
  enrollment: OperatorSetupBeginResponse | null;
  onBegin: (
    body: OperatorSetupBeginRequest,
  ) => Promise<ApiResult<OperatorSetupBeginResponse>>;
  onComplete: (
    body: OperatorSetupCompleteRequest,
  ) => Promise<ApiResult<void>>;
}

export function OperatorSetupScreen({
  enrollment,
  onBegin,
  onComplete,
}: OperatorSetupScreenProps) {
  const [setupToken, setSetupToken] = useState("");
  const [password, setPassword] = useState("");
  const [totpCode, setTotpCode] = useState("");
  const [message, setMessage] = useState<string | null>(null);
  const [isPending, setIsPending] = useState(false);
  const needsTotp = Boolean(enrollment?.totpSecret);

  async function handleBeginSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setIsPending(true);
    const result = await onBegin({ setupToken, password });
    setIsPending(false);
    setMessage(
      result.ok
        ? result.data.totpSecret
          ? "TOTP bind material loaded."
          : "Password captured. Complete setup to activate."
        : result.error,
    );
  }

  async function handleCompleteSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setIsPending(true);
    const result = await onComplete(
      needsTotp ? { setupToken, totpCode } : { setupToken },
    );
    setIsPending(false);
    setMessage(
      result.ok
        ? "Operator setup completed. Continue with operator login."
        : result.error,
    );
  }

  return (
    <div className={styles.stack}>
      <section className={`surface ${styles.stack}`}>
        <p className="eyebrow">Operator setup</p>
        <h2>Bootstrap from the CLI token</h2>
        <p className={styles.lead}>
          Run the setup CLI locally, paste the one-time token here, choose a
          password, then activate. When platform MFA is off, TOTP is not
          required during setup.
        </p>
        <form className="formStack" onSubmit={handleBeginSubmit}>
          <label className="fieldStack">
            <span>Setup token</span>
            <textarea
              rows={4}
              value={setupToken}
              onChange={(event) => setSetupToken(event.target.value)}
              placeholder="Paste the one-time setup token from OperatorSetupCli"
              required
            />
          </label>
          <label className="fieldStack">
            <span>Password</span>
            <input
              type="password"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              autoComplete="new-password"
              required
            />
          </label>
          <div className="actions">
            <button type="submit" disabled={isPending}>
              {isPending ? "Starting..." : "Begin setup"}
            </button>
          </div>
        </form>
      </section>

      <section className={`surface ${styles.resultPanel}`}>
        <h3>Complete setup</h3>
        {enrollment ? (
          <>
            {needsTotp ? (
              <div className={styles.resultGrid}>
                <article className="surface surfaceInset">
                  <h4 className={styles.cardTitle}>TOTP secret</h4>
                  <pre className={`${styles.codeBlock} ${styles.inlineCode}`}>
                    {enrollment.totpSecret}
                  </pre>
                </article>
                <article className="surface surfaceInset">
                  <h4 className={styles.cardTitle}>otpauth URL</h4>
                  <pre className={`${styles.codeBlock} ${styles.inlineCode}`}>
                    {enrollment.otpauthUrl}
                  </pre>
                </article>
              </div>
            ) : (
              <p className={styles.emptyState}>
                Password-only activation. No FactorBinding is required when MFA is off.
              </p>
            )}
            <form className="formStack" onSubmit={handleCompleteSubmit}>
              {needsTotp ? (
                <label className="fieldStack">
                  <span>TOTP code</span>
                  <input
                    inputMode="numeric"
                    value={totpCode}
                    onChange={(event) => setTotpCode(event.target.value)}
                    placeholder="123456"
                    required
                  />
                </label>
              ) : null}
              <div className="actions">
                <button type="submit" disabled={isPending}>
                  {isPending ? "Completing..." : "Complete setup"}
                </button>
              </div>
            </form>
          </>
        ) : (
          <p className={styles.emptyState}>Begin setup first to continue.</p>
        )}
        {message ? <p className="statusText">{message}</p> : null}
      </section>
    </div>
  );
}
