export type TokenContext = "operator" | "user" | "tenant";

export interface AccessTokenState {
  accessToken: string;
  context: TokenContext;
}

type TokenListener = (token: AccessTokenState | null) => void;

let activeToken: AccessTokenState | null = null;
const listeners = new Set<TokenListener>();

function notify() {
  for (const listener of listeners) {
    listener(activeToken);
  }
}

export function setToken(token: AccessTokenState) {
  activeToken = token;
  notify();
}

export function getToken() {
  return activeToken;
}

export function clearToken() {
  activeToken = null;
  notify();
}

export function subscribeToken(listener: TokenListener) {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
}
