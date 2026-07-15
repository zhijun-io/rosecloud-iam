import { useEffect, useState, useSyncExternalStore } from "react";
import {
  apiClient,
  type ApiResult,
  type CreateMemberInvitationRequest,
  type CreateMemberInvitationResponse,
  type CreateTenantRequest,
  type CreateTenantResponse,
  type DemoPermissionResponse,
  type InvitationAcceptBeginRequest,
  type InvitationAcceptBeginResponse,
  type InvitationAcceptCompleteRequest,
  type InvitationAcceptJoinRequest,
  type MembershipSummaryResponse,
  type OperatorLoginRequest,
  type OperatorLoginResponse,
  type OperatorSetupBeginRequest,
  type OperatorSetupBeginResponse,
  type OperatorSetupCompleteRequest,
  type TenantContextResponse,
  type UserLoginRequest,
  type AccessTokenResponse,
} from "./api/client";
import { logoutSession, refreshSession } from "./auth/session";
import {
  clearToken,
  getToken,
  subscribeToken,
  setToken,
  type TokenContext,
} from "./auth/tokenStore";
import styles from "./App.module.css";
import { AcceptInviteScreen } from "./screens/AcceptInviteScreen";
import {
  DemoPageScreen,
  type DemoCallState,
} from "./screens/DemoPageScreen";
import { HomeScreen, type View } from "./screens/HomeScreen";
import { InviteMemberScreen } from "./screens/InviteMemberScreen";
import { OperatorCreateTenantScreen } from "./screens/OperatorCreateTenantScreen";
import { OperatorLoginScreen } from "./screens/OperatorLoginScreen";
import { OperatorSetupScreen } from "./screens/OperatorSetupScreen";
import {
  SelectTenantScreen,
  type SelectedTenantState,
} from "./screens/SelectTenantScreen";
import { UserLoginScreen } from "./screens/UserLoginScreen";

interface NoticeState {
  tone: "info" | "success" | "error";
  text: string;
}

const viewLabels: Record<View, string> = {
  home: "Home",
  "operator-setup": "Operator setup",
  "operator-login": "Operator login",
  "create-tenant": "Create tenant",
  "accept-invite": "Accept invite",
  "user-login": "User login",
  "select-tenant": "Select tenant",
  "invite-member": "Invite member",
  demo: "Demo",
};

const viewHashes: Record<View, string> = {
  home: "#/",
  "operator-setup": "#/operator-setup",
  "operator-login": "#/operator-login",
  "create-tenant": "#/create-tenant",
  "accept-invite": "#/accept-invite",
  "user-login": "#/user-login",
  "select-tenant": "#/select-tenant",
  "invite-member": "#/invite-member",
  demo: "#/demo",
};

const hashToView = new Map<string, View>(
  Object.entries(viewHashes).map(([view, hash]) => [hash, view as View]),
);

function readViewFromHash(hash: string): View {
  return hashToView.get(hash) ?? "home";
}

function createDemoState(
  name: string,
  result: ApiResult<DemoPermissionResponse>,
): DemoCallState {
  if (result.ok) {
    return {
      label: name,
      status: result.status,
      ok: true,
      detail: `Granted ${result.data.permission}.`,
    };
  }

  return {
    label: name,
    status: result.status,
    ok: false,
    detail: result.error,
  };
}

export function App() {
  const token = useSyncExternalStore(subscribeToken, getToken, getToken);
  const [view, setView] = useState<View>(() =>
    readViewFromHash(window.location.hash),
  );
  const [notice, setNotice] = useState<NoticeState | null>(null);
  const [operatorEnrollment, setOperatorEnrollment] =
    useState<OperatorSetupBeginResponse | null>(null);
  const [tenantCreation, setTenantCreation] =
    useState<CreateTenantResponse | null>(null);
  const [inviteEnrollment, setInviteEnrollment] =
    useState<InvitationAcceptBeginResponse | null>(null);
  const [memberships, setMemberships] = useState<MembershipSummaryResponse[]>([]);
  const [selectedTenant, setSelectedTenant] =
    useState<SelectedTenantState | null>(null);
  const [memberInvitation, setMemberInvitation] =
    useState<CreateMemberInvitationResponse | null>(null);
  const [demoReadResult, setDemoReadResult] = useState<DemoCallState | null>(
    null,
  );
  const [demoAdminResult, setDemoAdminResult] = useState<DemoCallState | null>(
    null,
  );

  useEffect(() => {
    function handleHashChange() {
      setView(readViewFromHash(window.location.hash));
    }

    window.addEventListener("hashchange", handleHashChange);
    if (!window.location.hash) {
      window.location.hash = viewHashes.home;
    }

    return () => {
      window.removeEventListener("hashchange", handleHashChange);
    };
  }, []);

  function navigate(nextView: View) {
    window.location.hash = viewHashes[nextView];
    setView(nextView);
  }

  function showNotice(tone: NoticeState["tone"], text: string) {
    setNotice({ tone, text });
  }

  function activateToken(accessToken: string, context: TokenContext) {
    setToken({ accessToken, context });
  }

  function clearSessionState() {
    clearToken();
    setSelectedTenant(null);
    setDemoReadResult(null);
    setDemoAdminResult(null);
    showNotice("info", "Cleared in-memory access token and tenant context.");
  }

  async function handleOperatorSetupBegin(
    body: OperatorSetupBeginRequest,
  ): Promise<ApiResult<OperatorSetupBeginResponse>> {
    const result = await apiClient.operatorSetupBegin(body);
    if (result.ok) {
      setOperatorEnrollment(result.data);
      showNotice("success", "Operator setup begin succeeded.");
    } else {
      showNotice("error", result.error);
    }

    return result;
  }

  async function handleOperatorSetupComplete(
    body: OperatorSetupCompleteRequest,
  ): Promise<ApiResult<void>> {
    const result = await apiClient.operatorSetupComplete(body);
    if (result.ok) {
      showNotice("success", "Operator setup completed. Continue with login.");
      navigate("operator-login");
    } else {
      showNotice("error", result.error);
    }

    return result;
  }

  async function handleOperatorLogin(
    body: OperatorLoginRequest,
  ): Promise<ApiResult<OperatorLoginResponse>> {
    const result = await apiClient.operatorLogin(body);
    if (result.ok) {
      activateToken(result.data.accessToken, "operator");
      showNotice("success", "Operator access token loaded in memory.");
    } else {
      showNotice("error", result.error);
    }

    return result;
  }

  async function handleCreateTenant(
    body: CreateTenantRequest,
  ): Promise<ApiResult<CreateTenantResponse>> {
    const result = await apiClient.createTenant(body);
    if (result.ok) {
      setTenantCreation(result.data);
      showNotice("success", "Tenant created. Next, paste the invite token manually.");
      navigate("accept-invite");
    } else {
      showNotice("error", result.error);
    }

    return result;
  }

  async function handleInvitationBegin(
    body: InvitationAcceptBeginRequest,
  ): Promise<ApiResult<InvitationAcceptBeginResponse>> {
    const result = await apiClient.invitationAcceptBegin(body);
    if (result.ok) {
      setInviteEnrollment(result.data);
      showNotice("success", "Invitation begin succeeded.");
    } else {
      showNotice("error", result.error);
    }

    return result;
  }

  async function handleInvitationComplete(
    body: InvitationAcceptCompleteRequest,
  ): Promise<ApiResult<void>> {
    const result = await apiClient.invitationAcceptComplete(body);
    if (result.ok) {
      showNotice("success", "Invitation accepted. Continue with user login.");
      navigate("user-login");
    } else {
      showNotice("error", result.error);
    }

    return result;
  }

  async function handleInvitationJoin(
    body: InvitationAcceptJoinRequest,
  ): Promise<ApiResult<void>> {
    const result = await apiClient.invitationAcceptJoin(body);
    if (result.ok) {
      showNotice("success", "Existing user joined the tenant successfully.");
    } else {
      showNotice("error", result.error);
    }

    return result;
  }

  async function handleUserLogin(
    body: UserLoginRequest,
  ): Promise<ApiResult<AccessTokenResponse>> {
    const result = await apiClient.userLogin(body);
    if (result.ok) {
      activateToken(result.data.accessToken, "user");
      showNotice("success", "User access token loaded. Select a tenant next.");
      navigate("select-tenant");
    } else {
      showNotice("error", result.error);
    }

    return result;
  }

  async function handleLoadMemberships(): Promise<
    ApiResult<MembershipSummaryResponse[]>
  > {
    const result = await apiClient.listMemberships();
    if (result.ok) {
      setMemberships(result.data);
      showNotice("success", `Loaded ${result.data.length} membership(s).`);
    } else {
      showNotice("error", result.error);
    }

    return result;
  }

  async function handleSelectMembership(
    membership: MembershipSummaryResponse,
  ): Promise<ApiResult<TenantContextResponse>> {
    const result = await apiClient.selectTenantContext({
      membershipId: membership.membershipId,
    });
    if (result.ok) {
      activateToken(result.data.accessToken, "tenant");
      setSelectedTenant({ membership, context: result.data });
      showNotice("success", `Tenant context ready for ${membership.tenantName}.`);
      navigate("invite-member");
    } else {
      showNotice("error", result.error);
    }

    return result;
  }

  async function handleInviteMember(
    body: CreateMemberInvitationRequest,
  ): Promise<ApiResult<CreateMemberInvitationResponse>> {
    if (!selectedTenant) {
      const failure: ApiResult<CreateMemberInvitationResponse> = {
        ok: false,
        status: 0,
        error: "Select a tenant context first.",
        headers: new Headers(),
      };
      showNotice("error", failure.error);
      return failure;
    }

    const result = await apiClient.createMemberInvitation(
      selectedTenant.membership.tenantId,
      body,
    );
    if (result.ok) {
      setMemberInvitation(result.data);
      showNotice("success", "Tenant invite created.");
    } else {
      showNotice("error", result.error);
    }

    return result;
  }

  async function handleRefreshSession() {
    const result = await refreshSession();
    if (result.ok) {
      showNotice("success", "Access token refreshed from rc_refresh cookie.");
    } else {
      showNotice("error", result.error);
    }
  }

  async function handleLogoutSession() {
    const result = await logoutSession();
    setSelectedTenant(null);
    setDemoReadResult(null);
    setDemoAdminResult(null);
    if (result.ok) {
      showNotice("success", "Logged out. Refresh cookie revoked when applicable.");
    } else {
      showNotice("error", result.error);
    }
  }

  async function handleDemoRead(): Promise<ApiResult<DemoPermissionResponse>> {
    const result = await apiClient.demoRead();
    setDemoReadResult(createDemoState("demo:read", result));
    if (!result.ok) {
      showNotice("error", result.error);
    }
    return result;
  }

  async function handleDemoAdmin(): Promise<ApiResult<DemoPermissionResponse>> {
    const result = await apiClient.demoAdmin();
    setDemoAdminResult(createDemoState("demo:admin", result));
    if (!result.ok) {
      showNotice("error", result.error);
    }
    return result;
  }

  function renderView() {
    switch (view) {
      case "operator-setup":
        return (
          <OperatorSetupScreen
            enrollment={operatorEnrollment}
            onBegin={handleOperatorSetupBegin}
            onComplete={handleOperatorSetupComplete}
          />
        );
      case "operator-login":
        return <OperatorLoginScreen onLogin={handleOperatorLogin} />;
      case "create-tenant":
        return (
          <OperatorCreateTenantScreen
            result={tenantCreation}
            onCreate={handleCreateTenant}
          />
        );
      case "accept-invite":
        return (
          <AcceptInviteScreen
            enrollment={inviteEnrollment}
            onBegin={handleInvitationBegin}
            onComplete={handleInvitationComplete}
            onJoin={handleInvitationJoin}
          />
        );
      case "user-login":
        return <UserLoginScreen onLogin={handleUserLogin} />;
      case "select-tenant":
        return (
          <SelectTenantScreen
            memberships={memberships}
            selectedTenant={selectedTenant}
            onLoadMemberships={handleLoadMemberships}
            onSelectMembership={handleSelectMembership}
          />
        );
      case "invite-member":
        return (
          <InviteMemberScreen
            selectedTenant={selectedTenant}
            result={memberInvitation}
            onInvite={handleInviteMember}
          />
        );
      case "demo":
        return (
          <DemoPageScreen
            selectedTenant={selectedTenant}
            readResult={demoReadResult}
            adminResult={demoAdminResult}
            onRunRead={handleDemoRead}
            onRunAdmin={handleDemoAdmin}
          />
        );
      case "home":
      default:
        return (
          <HomeScreen
            currentContext={token?.context ?? null}
            activeTenantName={selectedTenant?.membership.tenantName ?? null}
            onNavigate={navigate}
          />
        );
    }
  }

  const tokenPreview = token?.accessToken.slice(0, 20);
  const canRefresh = token?.context === "user" || token?.context === "tenant";
  const canLogout = token !== null;

  return (
    <main className={styles.shell}>
      <div className={styles.backdrop} aria-hidden="true" />
      <div className={styles.frame}>
        <header className={`surface ${styles.header}`}>
          <div className={styles.headerTop}>
            <div className={styles.brandBlock}>
              <p className="eyebrow">RoseCloud IAM</p>
              <h1 className={styles.title}>Thin-slice console</h1>
              <p className={styles.subtitle}>
                Same-origin Vite + typed OpenAPI client + memory-only access
                tokens.
              </p>
            </div>
            <section className={`surface surfaceInset ${styles.sessionCard}`}>
              <h2 className={styles.sessionTitle}>Session</h2>
              <p className={styles.sessionCopy}>
                {token
                  ? `${token.context} token in memory`
                  : "No access token loaded"}
              </p>
              <p className={`${styles.sessionCopy} mono`}>
                {tokenPreview ? `${tokenPreview}...` : "n/a"}
              </p>
              <div className="actions">
                {canRefresh ? (
                  <button type="button" onClick={handleRefreshSession}>
                    Refresh session
                  </button>
                ) : null}
                {canLogout ? (
                  <button type="button" onClick={handleLogoutSession}>
                    Log out
                  </button>
                ) : null}
                <button type="button" className="ghostButton" onClick={clearSessionState}>
                  Clear access token
                </button>
              </div>
            </section>
          </div>

          <nav className={styles.nav} aria-label="Console views">
            {Object.entries(viewLabels).map(([candidateView, label]) => {
              const isActive = candidateView === view;
              return (
                <button
                  key={candidateView}
                  type="button"
                  aria-current={isActive ? "page" : undefined}
                  className={`${styles.navButton} ${
                    isActive ? styles.navButtonActive : ""
                  }`}
                  onClick={() => navigate(candidateView as View)}
                >
                  {label}
                </button>
              );
            })}
          </nav>
        </header>

        {notice ? (
          <section
            className={`surface ${styles.notice} ${
              notice.tone === "error"
                ? styles.noticeError
                : notice.tone === "success"
                  ? styles.noticeSuccess
                  : ""
            }`}
          >
            <p>{notice.text}</p>
          </section>
        ) : null}

        <section key={view} className={styles.viewFrame}>
          {renderView()}
        </section>
      </div>
    </main>
  );
}
