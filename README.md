# AutoDeploy / ZeroOps

Локальный MVP self-hosted PaaS: разработчик создаёт проект из GitHub, задаёт ветку, domain, runtime, env-переменные и получает понятную историю deployment в одной панели.

## Components

- `front/`: Next.js/TypeScript product dashboard на русском языке.
- `back/`: Java 21 / Spring Boot API, DTO-oriented REST API и PostgreSQL/Flyway migrations.
- `worker/`: Java 21 / Spring Boot PostgreSQL job consumer with graceful shutdown.
- `infra/compose/`: isolated local development stack.
- `infra/nginx/`: prepared local gateway configuration; it must not replace a production Nginx configuration.

## Local start

```bash
cp .env.example .env
# Replace the placeholder secrets in .env before storing anything sensitive.
docker compose --env-file .env -f infra/compose/docker-compose.yml up --build
```

Откройте `http://127.0.0.1:18080`; API health доступен по `http://127.0.0.1:18080/api/v1/health`.

## Что работает локально

- Светлые страницы Dashboard, Projects, Project Details с вкладками Environment, Deployments, Logs, Domains и Settings, Git Sources и New Project.
- CRUD проектов и env-переменных с ownership-проверкой. Secret-значения маскируются в API/UI.
- PostgreSQL очередь jobs: analyzer и deployment получают `queued/running/success/failed`, retry-поля и failure reason.
- Deployment history, построчные логи и authenticated SSE (`GET /api/v1/deployments/{id}/events`) с безопасным завершением при disconnect.
- AES-GCM шифрование secret env-переменных: значение не возвращается API/UI.
- GitHub connector проверяет personal access token через GitHub API, хранит его только зашифрованным и умеет получать branches. Token никогда не попадает в response или логи.
- GitHub webhook принимает `push`/`pull_request` delivery, проверяет `X-Hub-Signature-256` и дедуплицирует `X-GitHub-Delivery`.
- Servers API with owner checks, one-time enrollment, Agent credentials and persisted heartbeats.
- Separate Java Agent polls outbound-only, verifies expiring HMAC manifests, sends execution logs/status and defaults to `dry-run`.
- In the explicit Docker mode, the Agent creates a separate Docker network per project, applies fixed resource limits, determines an internal port only by static `Dockerfile EXPOSE` inspection, and allocates a public port only in `18100–18999`. It never edits host Nginx or binds `80/443`.
- Safe rollback creates a new queued deployment for the previous successful commit; it does not issue host commands from the UI.
- Optional read-only AI advisor sends only sanitized project metadata and cannot access secrets, Docker or SSH.

## Ограничения MVP сейчас

GitHub OAuth App (вместо PAT), transient `git clone`/build registry, routing state, proxy automation and Prometheus are not included. Agent Docker mode is opt-in and still requires a separately approved target-host integration plan. This is an intentional security boundary: neither UI, API nor worker receives arbitrary Docker/production-server control.

## Target-server Agent flow

1. Add a server in **Серверы**, then generate its one-time enrollment token.
2. On the target server (only after a reviewed plan), run the separate Agent with `CONTROL_PLANE_URL`, `AGENT_SERVER_ID`, `AGENT_CREDENTIAL` and the same `MANIFEST_SIGNING_KEY`.
3. Keep `AGENT_EXECUTION_MODE=dry-run` for connectivity verification. Docker mode is an explicit opt-in and is never enabled by the Control Plane.
4. In **Новый проект** enter only the GitHub URL, branch and `ONLINE` server. The control plane creates a signed deployment; the Agent reads the repository's root Dockerfile, allocates an AutoDeploy port and starts the isolated container.
5. Deployment logs/status return through the Agent and are available over browser SSE. After a success, the selected application port is saved on the project and the test URL is `http://SERVER_IP:ASSIGNED_PORT` until a domain is configured.

Never place the enrollment token, Agent credential, manifest key or AI key in Git.

## Local verification

```bash
docker compose --env-file .env -f infra/compose/docker-compose.yml config
docker compose --env-file .env -f infra/compose/docker-compose.yml ps
curl --fail http://127.0.0.1:18080/api/v1/health
curl --fail http://127.0.0.1:18080/api/v1/actuator/health
```

For host-based checks, Java 21 and Maven are required for `back/` and `worker/`; Node 22 is required for `front/`.

```bash
(cd back && mvn test)
(cd worker && mvn test)
(cd front && npm ci && npm run check && npm test && npm run build)
```

## Security boundaries

- API and worker images do **not** mount `/var/run/docker.sock`.
- User applications are not represented in this Compose project and must never join the control-plane network.
- The future deployment agent is a separate, restricted component. It may accept only signed, allowlisted manifests; never give the web/API/worker unrestricted Docker access.
- Dockerfile builds execute untrusted build steps. For production use, deploy only repositories you trust until builds are moved to an isolated builder with network, CPU, memory and registry policy controls.
- Store secrets only encrypted in the database. Set `SECRETS_MASTER_KEY` to `openssl rand -base64 32` in local `.env`; never commit or send that value.
- The gateway binds to loopback by default. Do not occupy production `80/443` or edit Nginx without a separate approved server plan.

## Future safe production deployment

Before any server deployment, create and approve a rollback-aware server integration plan. This repository does not change DNS, issue certificates or bind production 80/443. Local code can be committed; it is never pushed by the agent without separate approval.
