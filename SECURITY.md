# Security policy

## Do not commit

- API keys, tokens, cloud credentials, account identifiers
- customer or employee media
- faces or voices without documented consent
- provider request and response payloads containing personal data
- rendered video files

Use `.env.example` for variable names and keep real values in an untracked `.env` or secret manager.

## Provider integration requirements

Every real provider adapter must implement request timeout, bounded retry, idempotency, cost limit, content safety handling, and sanitized errors. Provider output must never be automatically published.

## Reporting a problem

Until a public repository and security contact are configured, report issues privately to the repository owner. Do not create public issues containing credentials or media samples.
