import { useState, type FormEvent } from "react";
import type {
  AccessTokenResponse,
  ApiResult,
  UserLoginRequest,
} from "../api/client";
import styles from "./ConsoleScreen.module.css";

interface UserLoginScreenProps {
  onLogin: (body: UserLoginRequest) => Promise<ApiResult<AccessTokenResponse>>;
}

export function UserLoginScreen({ onLogin }: UserLoginScreenProps) {
  const [email, setEmail] = useState("owner@example.com");
  const [password, setPassword] = useState("");
  const [totpCode, setTotpCode] = useState("");
  const [message, setMessage] = useState<string | null>(null);
  const [tokenPreview, setTokenPreview] = useState<string | null>(null);
  const [isPending, setIsPending] = useState(false);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setIsPending(true);
    const result = await onLogin({ email, password, totpCode });
    setIsPending(false);

    if (result.ok) {
      setTokenPreview(result.data.accessToken.slice(0, 48));
      setMessage(
        "User access token loaded in memory. Continue to Select tenant to mint tenant context.",
      );
      return;
    }

    setMessage(result.error);
  }

  return (
    <section className={`surface ${styles.stack}`}>
      <p className="eyebrow">User login</p>
      <h2>Authenticate a tenant owner or member</h2>
      <p className={styles.lead}>
        This sets the shared refresh cookie. Once logged in, you can rotate the
        user or tenant token with the Refresh session action in the header.
      </p>
      <form className="formStack" onSubmit={handleSubmit}>
        <label className="fieldStack">
          <span>Email</span>
          <input
            type="email"
            value={email}
            onChange={(event) => setEmail(event.target.value)}
            autoComplete="username"
            required
          />
        </label>
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
