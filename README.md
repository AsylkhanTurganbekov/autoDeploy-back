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
- Analyzer и deployment worker сохраняют результаты/логи без доступа к Docker socket.

## Ограничения MVP сейчас

GitHub OAuth App (вместо PAT), полноценное `git clone` с transient credential, удалённый deployment-agent, registry, Docker build/run, Traefik/HTTPS и Prometheus ещё не включены. Это осознанная security-граница: рабочий процесс не получает произвольные команды, токены или доступ к Docker/production server.

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
- Store secrets only encrypted in the database. Set `SECRETS_MASTER_KEY` to `openssl rand -base64 32` in local `.env`; never commit or send that value.
- The gateway binds to loopback by default. Do not occupy production `80/443` or edit Nginx without a separate approved server plan.

## Future safe production deployment

Before any server deployment, create and approve a rollback-aware server integration plan. This repository does not change DNS, issue certificates or bind production 80/443. Local code can be committed; it is never pushed by the agent without separate approval.
