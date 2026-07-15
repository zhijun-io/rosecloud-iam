import type { components, paths } from "../generated/openapi";
import { getToken } from "../auth/tokenStore";

type HttpMethod = "get" | "post";
type RoutePath = keyof paths;
type RouteOperation<Path extends RoutePath, Method extends HttpMethod> = Exclude<
  paths[Path][Method],
  undefined
>;
type ResponsesOf<Operation> = Operation extends { responses: infer Responses }
  ? Responses
  : never;
type JsonContent<Response> = Response extends {
  content: { "application/json": infer Body };
}
  ? Body
  : void;
type StatusContent<Responses, Code extends number> = Code extends keyof Responses
  ? JsonContent<Responses[Code]>
  : never;
type SuccessPayload<Operation> =
  | StatusContent<ResponsesOf<Operation>, 200>
  | StatusContent<ResponsesOf<Operation>, 201>
  | StatusContent<ResponsesOf<Operation>, 204>;
type JsonRequestBody<Operation> = Operation extends {
  requestBody: { content: { "application/json": infer Body } };
}
  ? Body
  : never;
type PathParams<Operation> = Operation extends {
  parameters: { path: infer Params };
}
  ? Params
  : never;

export type ApiSchemas = components["schemas"];
export type OperatorSetupBeginRequest = ApiSchemas["OperatorSetupBeginRequest"];
export type OperatorSetupBeginResponse = ApiSchemas["OperatorSetupBeginResponse"];
export type OperatorSetupCompleteRequest =
  ApiSchemas["OperatorSetupCompleteRequest"];
export type OperatorLoginRequest = ApiSchemas["OperatorLoginRequest"];
export type OperatorLoginResponse = ApiSchemas["OperatorLoginResponse"];
export type UserLoginRequest = ApiSchemas["UserLoginRequest"];
export type AccessTokenResponse = ApiSchemas["AccessTokenResponse"];
export type FactorChallengeRequiredResponse =
  ApiSchemas["FactorChallengeRequiredResponse"];
export type FactorChallengeRequest = ApiSchemas["FactorChallengeRequest"];
export type LoginResult =
  | OperatorLoginResponse
  | AccessTokenResponse
  | FactorChallengeRequiredResponse;

export function isFactorChallengeRequired(
  data: LoginResult,
): data is FactorChallengeRequiredResponse {
  return (
    !!data &&
    typeof data === "object" &&
    "status" in data &&
    data.status === "FACTOR_CHALLENGE_REQUIRED"
  );
}

export function hasAccessToken(
  data: LoginResult,
): data is OperatorLoginResponse | AccessTokenResponse {
  return !!data && typeof data === "object" && "accessToken" in data;
}
export type CreateTenantRequest = ApiSchemas["CreateTenantRequest"];
export type CreateTenantResponse = ApiSchemas["CreateTenantResponse"];
export type InvitationAcceptBeginRequest =
  ApiSchemas["InvitationAcceptBeginRequest"];
export type InvitationAcceptBeginResponse =
  ApiSchemas["InvitationAcceptBeginResponse"];
export type InvitationAcceptCompleteRequest =
  ApiSchemas["InvitationAcceptCompleteRequest"];
export type InvitationAcceptJoinRequest =
  ApiSchemas["InvitationAcceptJoinRequest"];
export type MembershipSummaryResponse = ApiSchemas["MembershipSummaryResponse"];
export type TenantContextRequest = ApiSchemas["TenantContextRequest"];
export type TenantContextResponse = ApiSchemas["TenantContextResponse"];
export type CreateMemberInvitationRequest =
  ApiSchemas["CreateMemberInvitationRequest"];
export type CreateMemberInvitationResponse =
  ApiSchemas["CreateMemberInvitationResponse"];
export type DemoPermissionResponse = ApiSchemas["DemoPermissionResponse"];

export interface ApiSuccess<T> {
  ok: true;
  status: number;
  data: T;
  headers: Headers;
}

export interface ApiFailure {
  ok: false;
  status: number;
  error: string;
  details?: unknown;
  headers: Headers;
}

export type ApiResult<T> = ApiSuccess<T> | ApiFailure;

const API_PREFIX = "/api";
const BASE_URL = "";

function buildPath(template: string, pathParams?: Record<string, string>) {
  return template.replace(/\{([^}]+)\}/g, (_, key) =>
    encodeURIComponent(pathParams?.[key] ?? ""),
  );
}

async function parseBody(response: Response) {
  if (response.status === 204) {
    return undefined;
  }

  const contentType = response.headers.get("content-type") ?? "";
  if (contentType.includes("application/json")) {
    return response.json();
  }

  const text = await response.text();
  return text || undefined;
}

function describeFailure(status: number, payload: unknown) {
  if (typeof payload === "string" && payload.trim()) {
    return payload.trim();
  }

  if (payload && typeof payload === "object") {
    try {
      return JSON.stringify(payload);
    } catch {
      return `Request failed with status ${status}`;
    }
  }

  return `Request failed with status ${status}`;
}

async function request<Path extends RoutePath, Method extends HttpMethod>({
  path,
  method,
  body,
  pathParams,
}: {
  path: Path;
  method: Method;
  body?: JsonRequestBody<RouteOperation<Path, Method>>;
  pathParams?: PathParams<RouteOperation<Path, Method>>;
}): Promise<ApiResult<SuccessPayload<RouteOperation<Path, Method>>>> {
  const token = getToken();
  const headers = new Headers({
    Accept: "application/json",
  });

  if (body !== undefined) {
    headers.set("Content-Type", "application/json");
  }

  if (token?.accessToken) {
    headers.set("Authorization", `Bearer ${token.accessToken}`);
  }

  const response = await fetch(
    `${BASE_URL}${API_PREFIX}${buildPath(
      path,
      pathParams as Record<string, string> | undefined,
    )}`,
    {
      method: method.toUpperCase(),
      headers,
      credentials: "include",
      body: body === undefined ? undefined : JSON.stringify(body),
    },
  );

  const payload = await parseBody(response);
  if (response.ok) {
    return {
      ok: true,
      status: response.status,
      data: payload as SuccessPayload<RouteOperation<Path, Method>>,
      headers: response.headers,
    };
  }

  return {
    ok: false,
    status: response.status,
    error: describeFailure(response.status, payload),
    details: payload,
    headers: response.headers,
  };
}

export const apiClient = {
  operatorSetupBegin(body: OperatorSetupBeginRequest) {
    return request({
      path: "/operator/setup/begin",
      method: "post",
      body,
    });
  },
  operatorSetupComplete(body: OperatorSetupCompleteRequest) {
    return request({
      path: "/operator/setup/complete",
      method: "post",
      body,
    });
  },
  operatorLogin(body: OperatorLoginRequest) {
    return request({
      path: "/operator/login",
      method: "post",
      body,
    });
  },
  operatorFactorChallenge(body: FactorChallengeRequest) {
    return request({
      path: "/operator/factor-challenge",
      method: "post",
      body,
    });
  },
  userFactorChallenge(body: FactorChallengeRequest) {
    return request({
      path: "/sessions/factor-challenge",
      method: "post",
      body,
    });
  },
  createTenant(body: CreateTenantRequest) {
    return request({
      path: "/operator/tenants",
      method: "post",
      body,
    });
  },
  invitationAcceptBegin(body: InvitationAcceptBeginRequest) {
    return request({
      path: "/invitations/accept/begin",
      method: "post",
      body,
    });
  },
  invitationAcceptComplete(body: InvitationAcceptCompleteRequest) {
    return request({
      path: "/invitations/accept/complete",
      method: "post",
      body,
    });
  },
  invitationAcceptJoin(body: InvitationAcceptJoinRequest) {
    return request({
      path: "/invitations/accept/join",
      method: "post",
      body,
    });
  },
  userLogin(body: UserLoginRequest) {
    return request({
      path: "/sessions/login",
      method: "post",
      body,
    });
  },
  userRefresh() {
    return request({
      path: "/sessions/refresh",
      method: "post",
    });
  },
  userLogout() {
    return request({
      path: "/sessions/logout",
      method: "post",
    });
  },
  listMemberships() {
    return request({
      path: "/me/memberships",
      method: "get",
    });
  },
  selectTenantContext(body: TenantContextRequest) {
    return request({
      path: "/me/tenant-context",
      method: "post",
      body,
    });
  },
  createMemberInvitation(
    tenantId: string,
    body: CreateMemberInvitationRequest,
  ) {
    return request({
      path: "/tenants/{tenantId}/invitations",
      method: "post",
      pathParams: { tenantId },
      body,
    });
  },
  demoRead() {
    return request({
      path: "/demo/read",
      method: "get",
    });
  },
  demoAdmin() {
    return request({
      path: "/demo/admin",
      method: "get",
    });
  },
};
