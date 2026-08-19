# Execution-plane gap analysis

## Реально работает сейчас

- JWT authentication, project ownership and encrypted project environment variables.
- Project CRUD, GitHub PAT verification, branch lookup and HMAC/idempotent webhook intake.
- Durable PostgreSQL deployment/jobs/log records and browser SSE consumption.
- A local Spring worker consumes jobs and persists a safe simulated deployment timeline.

## Simulation / demo (не execution plane)

- Server records, ownership, one-time enrollment and Agent heartbeats are implemented locally.
- The worker explicitly does not clone repositories, invoke Docker, route traffic or perform a real health check.
- Dashboard-level infrastructure metrics are still demo data; heartbeat persistence is real.
- Rollback queues the previous successful commit through the signed Agent path; persisted routing/image state remains required before production routing is enabled.

## Absent and required before production execution

1. Audit events and richer server RBAC.
2. Real source fetch, immutable image build and health verification in the isolated Agent executor.
3. Persisted routing version and Agent log ingestion with resume offsets.
4. Real source fetch, immutable image build, constrained candidate container, health check and idempotent cleanup.
5. An Agent-owned proxy strategy for clean servers; an approved isolated Nginx include strategy for the existing server only.
6. Persisted routing version, rollback operation and Agent log ingestion with resume offsets.
7. Backend-only Alem Cloud advisor with redaction, schema validation and user approval.

## Delivery boundary

The next production increment is routing/image-state persistence and an Agent-owned proxy for clean target hosts. A remote server remains read-only until an explicit integration plan is approved.
