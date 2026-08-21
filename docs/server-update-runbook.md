# Safe control-plane update runbook

This runbook is intentionally **not executed by this repository**. It protects existing server projects and system Nginx.

## Scope and expected changes

| Item | Change |
| --- | --- |
| `/opt/autodeploy/source/back` | update API source and Flyway migration `V9` |
| `/opt/autodeploy/source/front` | update web source |
| `autodeploy-control-api-1` | recreate from the isolated Compose project |
| `autodeploy-control-web-1` | recreate from the isolated Compose project |
| `autodeploy-agent-current` | recreate only after image build succeeds |
| PostgreSQL `autodeploy-control` database | additive migration: `project_services`, `deployments.service_path` |
| External ports | control plane remains `18080`; test applications only `18100–18999` |

Not touched: system Nginx, port 80/443, existing Growy/BuildMart containers, their networks/volumes, firewall, DNS, certificates, Docker daemon configuration.

## Preconditions

1. Rotate the previously exposed NITEC API credential and place the new value only in `/opt/autodeploy/runtime.env` (`0600`), not in Git or Compose source.
2. Confirm Docker health and record `docker ps`, `docker network ls`, disk/RAM, and the existing `autodeploy-control` container image IDs.
3. Back up only the AutoDeploy database: `pg_dump` from the AutoDeploy PostgreSQL container to a timestamped protected path. Verify the dump is non-empty.
4. Validate source using Java 21/Maven and `npm run build` before copying it to the host.
5. Render Compose without applying it: `docker compose ... config`.

## Apply sequence

1. Copy reviewed source to `/opt/autodeploy/source/{back,front}` without secrets.
2. Build API, web and Agent images; do not recreate any container yet. Inspect build status.
3. Recreate only `api` and `web` in Compose. Flyway applies the additive V9 migration on API start.
4. Verify `GET /api/v1/health`, `/actuator/health`, UI login, project list and `GET /api/v1/projects/{id}/services`.
5. Recreate only `agent` in `dry-run` mode first. Verify heartbeat and signed-manifest rejection test.
6. Run one known, allowed repository deployment in Docker mode; it must use a new port in `18100–18999`. Verify labels, network, health endpoint, SSE logs and that no container outside `io.autodeploy.managed=true` changed.

## Rollback

1. Stop only the new AutoDeploy API/web/Agent containers through their Compose project; never use broad Docker prune/reset commands.
2. Recreate the recorded prior AutoDeploy image tags. Existing app containers remain untouched.
3. If V9 itself must be reverted, restore the AutoDeploy-only PostgreSQL dump to a new/isolated database first; Flyway migrations are not destructively rolled back in place.
4. Check original API/UI health and the baseline `docker ps` diff. Keep failed-test logs for diagnosis.
