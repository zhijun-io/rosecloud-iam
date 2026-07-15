import { apiClient, type ApiResult, type AccessTokenResponse } from "../api/client";
import { clearToken, getToken, setToken } from "./tokenStore";

export async function refreshSession(): Promise<ApiResult<AccessTokenResponse>> {
  const token = getToken();
  if (!token) {
    return {
      ok: false,
      status: 0,
      error: "No active access token in memory.",
      headers: new Headers(),
    };
  }

  if (token.context === "operator") {
    return {
      ok: false,
      status: 0,
      error:
        "Operator refresh is intentionally not exposed in this console. Finish operator actions, then clear state before switching to user flows.",
      headers: new Headers(),
    };
  }

  const result = await apiClient.userRefresh();
  if (result.ok) {
    setToken({
      accessToken: result.data.accessToken,
      context: token.context,
    });
    return result;
  }

  if (result.status === 401) {
    clearToken();
  }

  return result;
}

export async function logoutSession(): Promise<ApiResult<void>> {
  const token = getToken();
  if (!token) {
    return {
      ok: false,
      status: 0,
      error: "No active access token in memory.",
      headers: new Headers(),
    };
  }

  if (token.context === "operator") {
    clearToken();
    return {
      ok: true,
      status: 204,
      data: undefined,
      headers: new Headers(),
    };
  }

  const result = await apiClient.userLogout();
  clearToken();
  return result;
}
