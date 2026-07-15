import { useState, type FormEvent } from "react";
import type {
  ApiResult,
  InvitationAcceptBeginRequest,
  InvitationAcceptBeginResponse,
  InvitationAcceptCompleteRequest,
  InvitationAcceptJoinRequest,
} from "../api/client";
import styles from "./ConsoleScreen.module.css";

interface AcceptInviteScreenProps {
  enrollment: InvitationAcceptBeginResponse | null;
  onBegin: (
    body: InvitationAcceptBeginRequest,
  ) => Promise<ApiResult<InvitationAcceptBeginResponse>>;
  onComplete: (
    body: InvitationAcceptCompleteRequest,
  ) => Promise<ApiResult<void>>;
  onJoin: (body: InvitationAcceptJoinRequest) => Promise<ApiResult<void>>;
}

export function AcceptInviteScreen({
  enrollment,
  onBegin,
  onComplete,
  onJoin,
}: AcceptInviteScreenProps) {
  const [token, setToken] = useState("");
  const [password, setPassword] = useState("");
  const [totpCode, setTotpCode] = useState("");
  const [joinPassword, setJoinPassword] = useState("");
  const [joinTotpCode, setJoinTotpCode] = useState("");
  const [message, setMessage] = useState<string | null>(null);
  const [isPending, setIsPending] = useState(false);

  async function handleBegin(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setIsPending(true);
    const result = await onBegin({ token, password });
    setIsPending(false);
    setMessage(
      result.ok ? "Invite begin succeeded. Finish TOTP below." : result.error,
    );
  }

  async function handleComplete(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setIsPending(true);
    const result = await onComplete({ token, totpCode });
    setIsPending(false);
    setMessage(
      result.ok
        ? "Invitation accepted. Continue with user login."
        : result.error,
    );
  }

  async function handleJoin(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setIsPending(true);
    const result = await onJoin({
      token,
      password: joinPassword,
      totpCode: joinTotpCode,
    });
    setIsPending(false);
    setMessage(
      result.ok
        ? "Existing user joined the tenant. Reload memberships after login."
        : result.error,
    );
  }

  return (
    <div className={styles.stack}>
      <section className={`surface ${styles.stack}`}>
        <p className="eyebrow">Invitation acceptance</p>
        <h2>Paste the invite token</h2>
        <p className={styles.lead}>
          Keep a single pasted token here, then choose either the new-user
          enrollment path or the existing-user join path.
        </p>

        <label className="fieldStack">
          <span>Invite token</span>
          <textarea
            rows={4}
            value={token}
            onChange={(event) => setToken(event.target.value)}
            placeholder="Paste token from outbox_message.payload or local logs"
            required
          />
        </label>
      </section>

      <section className={styles.split}>
        <article className={`surface ${styles.stack}`}>
          <h3>New user enroll</h3>
          <form className="formStack" onSubmit={handleBegin}>
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
                {isPending ? "Starting..." : "Begin accept"}
              </button>
            </div>
          </form>

          {enrollment ? (
            <>
              <pre className={`${styles.codeBlock} ${styles.inlineCode}`}>
                {enrollment.totpSecret}
              </pre>
              <pre className={`${styles.codeBlock} ${styles.inlineCode}`}>
                {enrollment.otpauthUrl}
              </pre>
              <form className="formStack" onSubmit={handleComplete}>
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
                <div className="actions">
                  <button type="submit" disabled={isPending}>
                    {isPending ? "Completing..." : "Complete accept"}
                  </button>
                </div>
              </form>
            </>
          ) : (
            <p className={styles.emptyState}>
              Begin the invitation first to reveal the TOTP secret.
            </p>
          )}
        </article>

        <article className={`surface ${styles.stack}`}>
          <h3>Existing user join</h3>
          <p className={styles.small}>
            This path proves password + TOTP for an already active user and does
            not reset that user&apos;s TOTP secret.
          </p>
          <form className="formStack" onSubmit={handleJoin}>
            <label className="fieldStack">
              <span>Password</span>
              <input
                type="password"
                value={joinPassword}
                onChange={(event) => setJoinPassword(event.target.value)}
                autoComplete="current-password"
                required
              />
            </label>
            <label className="fieldStack">
              <span>TOTP code</span>
              <input
                inputMode="numeric"
                value={joinTotpCode}
                onChange={(event) => setJoinTotpCode(event.target.value)}
                placeholder="123456"
                required
              />
            </label>
            <div className="actions">
              <button type="submit" disabled={isPending}>
                {isPending ? "Joining..." : "Join tenant"}
              </button>
            </div>
          </form>
        </article>
      </section>

      {message ? (
        <section className="surface">
          <p className="statusText">{message}</p>
        </section>
      ) : null}
    </div>
  );
}
