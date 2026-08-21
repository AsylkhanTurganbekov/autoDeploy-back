# API contract (local MVP)

All `/api/v1/**` endpoints except health, auth and GitHub webhooks require `Authorization: Bearer <JWT>`.

| Area | Endpoints |
| --- | --- |
| Auth | `POST /auth/register`, `POST /auth/login`, `POST /auth/logout` |
| Projects | `GET, POST /projects`; `GET, PUT, DELETE /projects/{id}` |
| Project details | `GET, PUT, DELETE /projects/{id}/variables/{key}`; `GET, POST /projects/{id}/deployments`; `POST /projects/{id}/analyze`; `GET /projects/{id}/analysis` |
| Deployments | `GET /deployments`; `GET /deployments/{id}`, `/logs`, `/events` (SSE) |
| GitHub | `GET, POST /github/connections`; `DELETE /github/connections/{id}`; `GET /github/branches?repository=owner/name` |
| Webhooks | `POST /webhooks/github`, HMAC SHA-256 in `X-Hub-Signature-256` and unique `X-GitHub-Delivery` |

Validation errors are `422`, unauthenticated requests `401`, other-user resources `403`, missing resources `404`, duplicate slug/delivery `409`. Secret values are deliberately omitted from responses.
# Additions: service plan and safe deployment

- `GET /api/v1/projects/{id}/services` — authenticated owner/admin list of Agent-discovered services; no repository credentials or environment values.
- `POST /api/v1/projects/{id}/services/{serviceId}/select` — owner/admin selects one public service. Private services return `422`.
- `POST /api/v1/projects/{id}/deployment-plan` — returns a validated `NITEC` or `RULE_BASED` advisory plan from sanitized metadata.
- `POST /api/v1/agent/{serverId}/deployments/{deploymentId}/plan` — Agent-only, authenticated by its per-server credential; persists the static plan after path/runtime/port validation.
- `POST /api/v1/projects/{id}/deployments` now accepts optional `serviceId`; it is ownership-checked and must be a selected/public project service.
