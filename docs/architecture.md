# FIRE architecture

## MVP boundaries

The first version proves job orchestration without spending money on video generation. The mock provider follows the same contract that a real asynchronous provider adapter will use later.

```mermaid
flowchart LR
    UI["React studio"] -->|REST| API["Spring Boot API"]
    API --> STORE["In-memory project store"]
    API --> QUEUE["Async generation orchestrator"]
    QUEUE --> PROVIDER["VideoProvider"]
    PROVIDER --> MOCK["Mock provider"]
    PROVIDER -. later .-> SORA["Sora adapter"]
    PROVIDER -. later .-> VEO["Veo adapter"]
    QUEUE --> STORE
```

## Project state

```mermaid
stateDiagram-v2
    [*] --> DRAFT
    DRAFT --> QUEUED: generation requested
    QUEUED --> PROCESSING: worker accepted
    PROCESSING --> COMPLETED: every scene completed
    PROCESSING --> FAILED: a scene failed
    FAILED --> QUEUED: explicit retry (planned)
```

Each scene independently moves through `PENDING`, `PROCESSING`, `COMPLETED`, or `FAILED`. A provider failure is recorded as a safe error code; raw credentials and response payloads are not returned to the UI.

## Provider contract

`VideoProvider` receives a normalized command and returns a provider job id plus a preview reference. Provider adapters own API authentication and payload conversion. The orchestrator owns state transitions, retries, budgets, and idempotency.

This separation prevents the product workflow from becoming coupled to one model vendor.

## Next persistence design

The in-memory store will be replaced with PostgreSQL tables for projects, scenes, attempts, assets, and cost events. A worker will claim jobs with an idempotency key. Input and output media will be stored in object storage, never in Git or database blobs.

## Safety decisions

- Real providers are disabled unless explicitly configured.
- Generation does not trigger publication or deployment.
- User media must be checked for consent, MIME type, size, and path safety.
- Provider prompts and metadata are auditable; secret values and raw sensitive payloads are not.
- Generated assets require retention and deletion policies before production use.
