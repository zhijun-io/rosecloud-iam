import { useState, type FormEvent } from "react";
import type {
  ApiResult,
  FactorChallengeRequest,
  FactorChallengeRequiredResponse,
  OperatorLoginRequest,
  OperatorLoginResponse,
} from "../api/client";
import { hasAccessToken, isFactorChallengeRequired } from "../api/client";
import styles from "./ConsoleScreen.module.css";

interface OperatorLoginScreenProps {
  onLogin: (
    body: OperatorLoginRequest,
  ) => Promise<
    ApiResult<OperatorLoginResponse | FactorChallengeRequiredResponse>
  >;
  onFactorChallenge: (
    body: FactorChallengeRequest,
  ) => Promise<ApiResult<OperatorLoginResponse>>;
}

export function OperatorLoginScreen({
  onLogin,
  onFactorChallenge,
}: OperatorLoginScreenProps) {
  const [password, setPassword] = useState("");
  const [challenge, setChallenge] =
    useState<FactorChallengeRequiredResponse | null>(null);
  const [totpCode, setTotpCode] = useState("");
  const [recoveryCode, setRecoveryCode] = useState("");
  const [message, setMessage] = useState<string | null>(null);
  const [tokenPreview, setTokenPreview] = useState<string | null>(null);
  const [isPending, setIsPending] = useState(false);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setIsPending(true);
    const result = await onLogin({ password });
    setIsPending(false);

    if (!result.ok) {
      setMessage(result.error);
      return;
    }

    if (isFactorChallengeRequired(result.data)) {
      setChallenge(result.data);
      setMessage("Factor challenge required. Enter TOTP or a recovery code.");
      return;
    }

    if (hasAccessToken(result.data)) {
      setChallenge(null);
      setTokenPreview(result.data.accessToken.slice(0, 48));
      setMessage("Operator access token loaded in memory.");
    }
  }

  async function handleChallenge(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!challenge) {
      return;
    }
    setIsPending(true);
    const bindingId = challenge.bindings[0]?.id;
    const result = await onFactorChallenge({
      challengeId: challenge.challengeId,
      bindingId: recoveryCode ? undefined : bindingId,
      totpCode: recoveryCode ? undefined : totpCode,
      recoveryCode: recoveryCode || undefined,
    });
    setIsPending(false);

    if (!result.ok) {
      setMessage(result.error);
      return;
    }

    setChallenge(null);
    setTokenPreview(result.data.accessToken.slice(0, 48));
    setMessage("Operator access token loaded in memory.");
  }

  return (
    <section className={`surface ${styles.stack}`}>
      <p className="eyebrow">Operator login</p>
      <h2>Authenticate the platform operator</h2>
      <p className={styles.lead}>
        Password-only when MFA is off or no FactorBinding exists. Otherwise the
        server returns a FactorChallenge before issuing a session.
      </p>

      <form className="formStack" onSubmit={handleSubmit}>
        <label className="fieldStack">
          <span>Password</span>
          <input
            type="password"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            autoComplete="current-password"
            required
          />
        </label>
        <div className="actions">
          <button type="submit" disabled={isPending}>
            {isPending ? "Logging in..." : "Login"}
          </button>
        </div>
      </form>

      {challenge ? (
        <form className="formStack" onSubmit={handleChallenge}>
          <p className={styles.small}>
            FactorChallenge {challenge.challengeId}. Bindings:{" "}
            {challenge.bindings.map((binding) => binding.softLabel).join(", ")}
          </p>
          <label className="fieldStack">
            <span>TOTP code</span>
            <input
              inputMode="numeric"
              value={totpCode}
              onChange={(event) => setTotpCode(event.target.value)}
              placeholder="123456"
            />
          </label>
          <label className="fieldStack">
            <span>Recovery code (optional)</span>
            <input
              value={recoveryCode}
              onChange={(event) => setRecoveryCode(event.target.value)}
              placeholder="leave blank to use TOTP"
            />
          </label>
          <div className="actions">
            <button type="submit" disabled={isPending}>
              {isPending ? "Verifying..." : "Complete challenge"}
            </button>
          </div>
        </form>
      ) : null}

      {tokenPreview ? (
        <article className="surface surfaceInset">
          <h3 className={styles.cardTitle}>Access token preview</h3>
          <pre className={`${styles.codeBlock} ${styles.inlineCode}`}>
            {tokenPreview}...
          </pre>
        </article>
      ) : null}
      {message ? <p className="statusText">{message}</p> : null}
    </section>
  );
}
