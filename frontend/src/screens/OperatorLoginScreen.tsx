import { useState, type FormEvent } from "react";
import type {
  ApiResult,
  OperatorLoginRequest,
  OperatorLoginResponse,
} from "../api/client";
import styles from "./ConsoleScreen.module.css";

interface OperatorLoginScreenProps {
  onLogin: (
    body: OperatorLoginRequest,
  ) => Promise<ApiResult<OperatorLoginResponse>>;
}

export function OperatorLoginScreen({ onLogin }: OperatorLoginScreenProps) {
  const [password, setPassword] = useState("");
  const [totpCode, setTotpCode] = useState("");
  const [message, setMessage] = useState<string | null>(null);
  const [tokenPreview, setTokenPreview] = useState<string | null>(null);
  const [isPending, setIsPending] = useState(false);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setIsPending(true);
    const result = await onLogin({ password, totpCode });
    setIsPending(false);

    if (result.ok) {
      setTokenPreview(result.data.accessToken.slice(0, 48));
      setMessage("Operator access token loaded in memory.");
      return;
    }

    setMessage(result.error);
  }

  return (
    <section className={`surface ${styles.stack}`}>
      <p className="eyebrow">Operator login</p>
      <h2>Authenticate the platform operator</h2>
      <p className={styles.lead}>
        This login also sets the refresh cookie, but the console deliberately
        keeps only the short-lived access token in memory.
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
            {isPending ? "Logging in..." : "Login"}
          </button>
        </div>
      </form>

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
