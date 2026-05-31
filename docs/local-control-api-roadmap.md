# Local Control API Roadmap

This roadmap defines a community-oriented, safety-first path for adding programmatic control to sdrtrunk. The work is intentionally general-purpose: it should help scanner hobbyists, stream operators, public-safety audio archivists, and automation users without embedding any one operator's local assumptions.

## Design principles

- **Safe by default:** the API is disabled unless explicitly enabled, binds to loopback by default, and requires a bearer token for all control surfaces when configured.
- **Read before write:** expose observable state before mutation. Write operations must be explicit, auditable, and dry-run capable.
- **No secret leakage:** stream passwords, API tokens, RadioReference credentials, and other secrets must never appear in responses, logs, exports, or examples.
- **Community portability:** profiles, bundles, and tooling should use stable schemas and import/export validation rather than machine-specific paths.
- **Cross-platform:** Windows, macOS, and Linux must remain first-class targets.
- **OpenAPI-first:** endpoints must be documented in `docs/openapi/local-control-api.yaml` as they are added.
- **TDD where practical:** add unit or integration tests for each endpoint, config behavior, validation path, and safety guard.

## Phase 1 — API foundation and status endpoint

**Goal:** ship a minimal embedded localhost API that can be enabled safely and verified independently of the GUI.

### Scope

- Add an embedded HTTP server using JDK-provided `com.sun.net.httpserver.HttpServer` to avoid adding a heavyweight framework dependency.
- Add configuration model:
  - `sdrtrunk.api.enabled` default `false`.
  - `sdrtrunk.api.host` default `127.0.0.1`.
  - `sdrtrunk.api.port` default `9997` for runtime use; tests may use `0` for an ephemeral port.
  - token from JVM property `sdrtrunk.api.token` or environment `SDRTRUNK_API_TOKEN`; never persisted by default.
- Add `/api/v1/status` endpoint returning application/runtime health only.
- Add `/api/v1/openapi.yaml` endpoint returning the local OpenAPI document.
- Add bearer-token middleware support for API endpoints when a token is configured.
- Start the server from the existing `SDRTrunk` lifecycle only when enabled.
- Stop the server during normal application shutdown.

### Acceptance criteria

- API is disabled by default.
- Default bind address is loopback.
- `/api/v1/status` returns HTTP 200 and JSON when enabled.
- If a token is configured, missing or invalid `Authorization: Bearer <token>` returns HTTP 401.
- Status responses do not expose the token or other secrets.
- OpenAPI file documents Phase 1 endpoints and security behavior.
- Tests cover defaults, token authorization, status JSON, and OpenAPI availability.

## Phase 2 — Read-only models

**Goal:** allow external tools to inspect active configuration and runtime state without mutating anything.

### Scope

- Add read-only endpoints for:
  - playlists and playlist metadata
  - channels and enabled/auto-start state
  - aliases and alias lists
  - tuners and discovered tuner state
  - broadcast configurations with secrets redacted
  - active processing channels and summary metrics
  - recent decode/event-log metadata where already available
- Define stable DTOs that avoid leaking internal mutable objects.
- Add pagination/filtering for large alias and event datasets.
- Add schema versioning and `generatedAt` timestamps.

### Acceptance criteria

- All read endpoints require auth when a token is configured.
- Broadcast credentials and service tokens are redacted.
- DTOs are covered by serialization tests.
- Endpoint tests use representative playlist/alias fixtures.
- OpenAPI schemas are updated.

## Phase 3 — Safe write endpoints

**Status:** Initial runtime-control slice implemented in the fork: dry-run-capable channel start/stop endpoints, validation errors, and append-only in-memory audit records for applied mutations. Persisted playlist/alias/broadcast edits remain future Phase 3 work and still require snapshot/rollback support before implementation.

**Goal:** allow controlled editing and runtime actions without making accidental damage easy.

### Scope

- Add validated mutation endpoints for common operations:
  - enable/disable channel
  - start/stop channel processing
  - update channel metadata and decoder settings
  - update alias/talkgroup records
  - reorder auto-start priority
  - update non-secret broadcast metadata
- Require `dry_run=true` support for every write endpoint.
- Return structured validation results before applying changes.
- Add an append-only audit log with timestamp, endpoint, actor mode, changed fields, dry-run flag, and result.
- Add backup/snapshot creation before persisted playlist edits.
- Make destructive operations explicit and reversible where possible.

### Acceptance criteria

- Dry-run returns planned diffs and validation warnings without writing files or changing runtime state.
- Applied changes write audit records.
- Secret fields are write-only or redacted in all read-back responses.
- Invalid writes fail with HTTP 400 and actionable validation messages.
- Tests cover dry-run, validation failure, successful mutation, audit records, and rollback/snapshot behavior.

## Phase 4 — Operator tooling: CLI and lightweight web UI

**Goal:** make the API usable by humans and automation without requiring custom scripts.

### Scope

- Add `sdrtrunkctl` CLI for common inspection and edit flows:
  - status
  - list channels/tuners/broadcasts
  - start/stop channel
  - import/export profile bundle
  - validate playlist/profile
  - bulk alias/talkgroup edit preview/apply
- Add a lightweight local web UI or static operator panel served from the API.
- Support config discovery via environment variables and local config file.
- Add examples for Windows scheduled tasks/PowerShell, Linux systemd, and macOS launchd.

### Acceptance criteria

- CLI can operate against a token-protected local API.
- CLI never prints secrets.
- Web UI works from localhost and documents how to secure access.
- CLI and UI use the same OpenAPI-backed schemas where practical.
- Cross-platform smoke tests pass in CI.

## Phase 5 — Reliability watchdog and daemon/headless operations

**Goal:** improve long-running stream reliability and unattended operation.

### Scope

- Add stream and processing health signals:
  - broadcast connected/disconnected state
  - last successful audio frame timestamp
  - reconnect counts
  - active channel count
  - tuner lock/error state where available
- Add watchdog policy hooks:
  - notify only
  - restart broadcast
  - restart channel
  - run external command/webhook
- Add daemon/headless documentation and packaging improvements.
- Expose safe health endpoints for monitoring systems.
- Add alert/webhook templates with secret-safe payloads.

### Acceptance criteria

- Watchdog defaults to observe-only.
- Automatic restarts require explicit opt-in.
- Health status can be consumed by uptime monitors without exposing secrets.
- Long-running reliability tests or simulations cover reconnect logic.
- Documentation covers Windows service-style operation and common stream recovery patterns.

## Initial sequencing

1. Land Phase 1 on a narrow PR.
2. Add Phase 2 read-only models in small endpoint groups.
3. Add Phase 3 writes only after DTOs and validation are stable.
4. Build CLI before web UI if tradeoffs are required.
5. Treat watchdog/daemon work as reliability infrastructure after the API has stable control primitives.
