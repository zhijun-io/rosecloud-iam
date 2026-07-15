# Landable MFA — document change list

**Status: executed** via [Write landable MFA spec from change list](https://github.com/zhijun-io/rosecloud-iam/issues/19) (landed prose in `iam-v1.md`, `CONTEXT.md`, ADR 0004, architecture/README). Kept as index.

Source map: [Wayfinder: Pluggable MFA with platform default off](https://github.com/zhijun-io/rosecloud-iam/issues/9).  
Vocabulary: `CONTEXT.md` — MfaFeature / Factor / FactorBinding / FactorChallenge / RecoveryCode / StepUp.

**Owner column** = who drafts the edit when the writing pass runs (roles, not GitHub assignees).

| Status | Meaning |
| --- | --- |
| Ready | Decisions landed; can write now |
| Partial | Mostly decided; one fog note blocks full wording |
| Deferred | Waiting on fog tickets |
| Out | Explicitly out of this destination |

---

## 1. `CONTEXT.md` (glossary)

| Item | Change | Status | Owner |
| --- | --- | --- | --- |
| Credential / MfaFeature / Factor / FactorBinding / FactorChallenge / StepUp | Ensure definitions match closed tickets #10–#14 (working tree may already have these; reconcile on writing branch) | Ready | Domain |
| _Avoid_ lists | Keep Permission ≠ Capability; Challenge only as FactorChallenge | Ready | Domain |
| FactorProvider / FactorCatalog | **Do not** add — SPI/implementation, not glossary | Out | — |

---

## 2. `docs/spec/iam-v1.md` (behavior contract)

| Section | Change | Status | Owner |
| --- | --- | --- | --- |
| §2 Non-goals | Note no Tenant-forced MFA in this revision; no second concrete Factor channel | Ready | Spec |
| §3 Actors | Drop “Owner/Operator 必须绑定 TOTP”; Operator gains “flip MfaFeature”; reset language → FactorBinding / Factor | Ready | Spec |
| §4.2 Tenant lifecycle | First Owner accepting invite: **no** mandatory TOTP gate; Tenant → ACTIVE without FactorBinding when MfaFeature off or unbound | Ready | Spec |
| §4.x Invitation accept | Remove “若策略要求则绑定 TOTP” / tenant MFA gate; voluntary FactorBinding under MfaFeature only | Ready | Spec |
| §5.1 Methods | Password + pluggable Factors; TOTP is the first Factor kind, not the only named peer forever | Ready | Spec |
| §5.2 MFA policy | **Replace** with **MfaFeature** lifecycle (default off; Operator+StepUp; off→on / on→off rules from #11). Delete Tenant-forced MFA policy for this revision | Ready | Spec |
| §5.3 Recovery | RecoveryCode dedicated path (not Factor); challenge sidecan; Operator reset clears bindings (#17) | Ready | Spec |
| §5.4 Brute-force | State shared principal+IP bucket for password + FactorChallenge (#12) | Ready | Spec |
| §5.5 Enumeration | Fuzzy until password OK; pending FactorChallenge rules (#12) | Ready | Spec |
| New § (login SM) | Three-way login + pending FactorChallenge before LoginSession (#12); client picks binding when many (#13) | Ready | Spec |
| §6.2 TenantContext issuance | Drop “MFA 策略已满足” tied to tenant policy; no enroll gate for TenantContext under this revision | Ready | Spec |
| §6.x Session revoke triggers | “TOTP/恢复码变更” → FactorBinding / recovery credential changes (recovery wording deferred) | Partial | Spec |
| StepUp (§ high-risk) | Password-only if zero FactorBinding; else client-selected FactorChallenge; 5m LoginSession-bound window; login seeds window (#13) | Ready | Spec |
| §8 PlatformOperator | Setup: password (+ optional FactorBinding if MfaFeature on / chosen); flip MfaFeature; AuditEvent on successful flip; disaster CLI reset remains | Ready | Spec |
| §9 Audit | Successful MfaFeature flip event fields (#11) | Ready | Spec |

---

## 3. New ADR

| Item | Change | Status | Owner |
| --- | --- | --- | --- |
| Title (sketch) | **Pluggable Factors behind MfaFeature (default off)** | Ready | Architecture |
| Body sketch | Why separate MfaFeature from Permission/Capability; FactorProvider/FactorCatalog as module seam; inherit existing TOTP rows as FactorBinding; reject tenant-forced MFA for this revision; point at prototype `prototype/factor-provider` | Ready | Architecture |
| Migration note | Behavioral inherit: verified TOTP ⇒ one totp FactorBinding; pending ≠ Binding; dual-read/schema deferred (#18) | Ready | Architecture |
| Filename | Next free: `docs/adr/0004-….md` (confirm number at write time) | Ready | Architecture |

---

## 4. Supporting docs (light touch)

| Doc | Change | Status | Owner |
| --- | --- | --- | --- |
| `docs/architecture.md` | Package ownership: Factor / FactorBinding / catalog live under `identity` (or note `operator` shares SPI); sequence diagram MFA gate → MfaFeature | Ready | Architecture |
| `README.md` | One-line: MFA optional via MfaFeature; TOTP as first Factor | Ready | Docs |
| `docs/research/mfa-totp-coupling.md` | Keep as evidence; add “superseded by landable revision” pointer when V1 lands | Ready | Spec |
| `docs/spec/prd-thin-slice.md` | **Do not** rewrite thin-slice DoD (map Out of scope) | Out | — |
| `docs/plans/0001-thin-slice.md` | **Do not** revise retrospective DoD; optional single “forward V1 diverges: …” footnote only if writers want a pointer | Out | — |
| `docs/adr/0002-…` | Optional one-line cross-link that global TOTP becomes FactorBinding under User | Ready | Architecture |

---

## 5. Writing-pass order (suggested)

1. `CONTEXT.md` reconcile  
2. New ADR 0004 sketch → accepted  
3. `iam-v1.md` §5.2 / login / StepUp / §8 (behavior core)  
4. Remaining `iam-v1.md` actor/lifecycle cross-links  
5. `architecture.md` + `README.md`  
6. ADR migration paragraph + §5.3 RecoveryCode (decisions landed)  

---

## 6. Blocked by remaining fog

_(none — writing pass unblocked; see ticket Write landable MFA spec from change list)_
