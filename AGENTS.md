# FIRE project instructions

## Purpose

FIRE is an AI-assisted short-form video production pipeline. The product value is reliable orchestration: scene planning, provider jobs, retries, progress, partial regeneration, cost controls, and deterministic rendering.

## Repository layout

- `backend/`: Java 21 and Spring Boot API
- `frontend/`: React and TypeScript studio UI
- `docs/`: architecture and portfolio evidence
- `compose.yaml`: local full-stack runtime

## Working rules

1. Keep provider-specific code behind `VideoProvider` adapters.
2. Default to `MockVideoProvider`; paid generation must require explicit configuration and confirmation.
3. Never commit API keys, customer media, faces without consent, or provider response bodies containing sensitive data.
4. Treat generation as an asynchronous job. Preserve state transitions and make retries idempotent.
5. Do not let AI output directly execute shell, FFmpeg, deployment, or deletion commands.
6. Validate file type, size, path containment, and ownership before processing media.
7. Record prompt, provider, model, input asset hash, job id, cost estimate, and timestamps for reproducibility.
8. Keep generated media out of Git. Use synthetic fixtures in tests and demos.

## Verification

- Backend: `docker build -t fire-api-dev ./backend`
- Frontend: `npm.cmd --prefix frontend run build`
- Full stack: `docker compose up --build`
- Before commit: inspect `git diff --check` and confirm `.env` or generated media is not staged.

## Definition of done

- Success, provider failure, timeout, retry, and restart behavior are covered.
- API errors are structured and do not expose credentials or raw provider payloads.
- The UI shows queued, processing, completed, and failed states.
- README and architecture docs are updated when contracts or state transitions change.
- Update `PORTFOLIO.md` whenever a feature, architecture decision, verification result, metric, or project limitation changes. Keep implemented and planned work clearly separated.
