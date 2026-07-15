# Agent skills

### Issue tracker

GitHub Issues via `gh`. See `docs/agents/issue-tracker.md`.

### Triage labels

Default vocabulary: `needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`, `wontfix`. See `docs/agents/triage-labels.md`.

### Domain docs

Single-context: root `CONTEXT.md` + `docs/adr/`. See `docs/agents/domain.md`.

### Local development

Taskfile, Docker Compose, timezone, optional SDKMAN: `docs/local-dev.md`.

### Git commits

Use **[Conventional Commits](https://www.conventionalcommits.org/)**. Agents must not invent free-form subjects.

- Format: `<type>(optional-scope): <description>`
- Common types: `feat`, `fix`, `docs`, `chore`, `refactor`, `test`, `ci`, `build`
- Description: imperative, concise; explain **why** in the body when needed
- One logical change per commit; do not mix unrelated DX and feature work
- Only commit when explicitly asked; never `--no-verify` / force-push `main` unless the human says so
- Example: `chore(devex): add Task/Compose tooling and Asia/Shanghai defaults`
