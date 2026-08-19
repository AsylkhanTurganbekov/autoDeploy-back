# Execution-plane gap analysis

## Реально работает сейчас

- JWT authentication, project ownership and encrypted project environment variables.
- Project CRUD, GitHub PAT verification, branch lookup and HMAC/idempotent webhook intake.
- Durable PostgreSQL deployment/jobs/log records and browser SSE consumption.
- A local Spring worker consumes jobs and persists a safe simulated deployment timeline.

## Simulation / demo (не execution plane)

- `GET /api/v1/servers` returns a static server card; there is no `Server` database model, ownership or enrollment.
- The worker explicitly does not clone repositories, invoke Docker, route traffic or perform a real health check.
- Monitoring/server resources are demo data, not Agent heartbeats.
- No current/previous running image, routing state or rollback operation exists.

## Absent and required before production execution

1. Multi-server models, RBAC, one-time enrollment, heartbeats and audit events.
2. A separate Agent with a limited Docker executor and no remote root/SSH credentials in control plane.
3. Signed, expiring, server-bound deployment manifests; no raw commands or repository Compose files.
4. Real source fetch, immutable image build, constrained candidate container, health check and idempotent cleanup.
5. An Agent-owned proxy strategy for clean servers; an approved isolated Nginx include strategy for the existing server only.
6. Persisted routing version, rollback operation and Agent log ingestion with resume offsets.
7. Backend-only Alem Cloud advisor with redaction, schema validation and user approval.

## Delivery boundary

The next code increment is Phase 1: real Servers + enrollment in the local control plane. It will not connect to or modify any remote server. A remote server remains read-only until an explicit integration plan is approved.
