# Architecture decision record

The control plane uses Next.js, Java 21/Spring Boot, PostgreSQL and a separate Spring worker. The API owns persistent users, projects, encrypted secrets, GitHub connections, deployments, logs, webhook delivery IDs and jobs. The worker atomically claims PostgreSQL jobs with `FOR UPDATE SKIP LOCKED`; this keeps job history observable and avoids a second broker dependency in the first release.

The browser calls the API with short-lived JWT bearer tokens. Project ownership is enforced on every project and deployment route. Deployment status and logs are durable database records; the API exposes them as authenticated Server-Sent Events and cancels the emitter task when a client disconnects.

GitHub PATs are verified against GitHub before saving and AES-GCM encrypted at rest using a non-database `SECRETS_MASTER_KEY`. Webhook input has an HMAC SHA-256 signature check and delivery-ID idempotency. Tokens, decrypted env values and webhook signatures are never returned or logged.

An eventual server agent is a separate deployment boundary. It must verify a signed allowlisted manifest and reject privileged mode, host networking, host mounts, published application ports and Docker Compose supplied by a user repository.

## Multi-server execution plane

The control plane is installed once. Every target server runs a separate AutoDeploy Agent and initiates its own outbound authenticated connection to the control plane; the control plane stores neither SSH nor root passwords. A server is owned by a user/team and moves through `PENDING`, `ONLINE`, `OFFLINE`, `DISABLED` and `MAINTENANCE` states.

Enrollment uses a short-lived, one-time token. Only a token hash, expiry, use/revoke timestamp and audit event are stored. After enrollment, the Agent uses a rotated per-agent credential. Heartbeats report only Agent-managed container IDs plus OS, Docker/Agent version, capabilities and resource metrics.

## Manifest and execution boundary

The API signs an expiring versioned manifest containing deployment/project/server IDs, immutable commit, validated runtime/build strategy, application port, health path, resource limits, sealed environment payload, image tag, routing intent and nonce. The Agent validates signature, expiration, server ID, nonce and every field against an allowlist. It rejects commands, Compose files, LLM text and unknown fields.

An Agent labels every managed resource `managed-by=autodeploy`, `project-id`, `deployment-id` and `server-id`; it can only read/remove resources with those labels. Candidate applications are non-root, have dropped capabilities, `no-new-privileges`, resource/pid limits and no host ports. Only an approved proxy receives external traffic.

## Routing and rollback

On clean target servers the Agent may own an isolated AutoDeploy proxy. On the existing production server, the current Nginx remains the owner of 80/443. AutoDeploy routing requires a separately approved include of `/etc/nginx/autodeploy-sites/*.conf`; each project has its own config. The sequence is candidate start, health check, atomic config write, `nginx -t`, graceful reload, route verification. No Nginx restart is allowed.

Every successful deployment records an immutable image reference and previous active version. Rollback is server-bound, idempotent and never deletes data volumes implicitly.

## Threat model

- Untrusted repository: no Compose input or raw shell commands; build/runtime are allowlisted.
- Compromised UI/API/LLM: Agent rejects unsigned or out-of-scope manifests.
- Cross-project access: separate ownership checks, Docker labels/networks/volumes and resource limits.
- Secret exposure: values are encrypted at rest, sealed for delivery and redacted from logs/API/UI.
- Existing workloads: Agent never touches resources without `managed-by=autodeploy`; current Nginx config is untouched until explicit approval.
