# Read-only server audit — 2026-08-20

No server configuration, container, process, firewall rule, DNS record or certificate was changed during this audit.

## Observed state

- Ubuntu 24.04.4 LTS, KVM VM; uptime was about 24 days.
- Memory: 7.7 GiB total, about 5.4 GiB available; root disk: 99 GiB total, 70 GiB free.
- Host Nginx owns `0.0.0.0:80` and `[::]:80`.
- Existing workloads include Growy and BuildMart. Their Docker networks are `growy-nit_default` and `buildmart-network`; they must not be joined by AutoDeploy.
- Existing host listeners include public `3000`, `6379` and `18000`, plus loopback `8080`, `8083`, `19000` and `19001`. AutoDeploy must not claim any of them.
- UFW reports inactive. This is existing infrastructure state; it was not changed.

## Approved-safe isolation proposal (not applied)

1. Create a dedicated non-login `autodeploy` system user and `/opt/autodeploy` directory only after explicit change approval.
2. Run Control Plane and each target Agent as separate Compose projects with dedicated `autodeploy-control` / `autodeploy-agent` networks and volumes. Never reuse Growy/BuildMart networks or volumes.
3. Keep the Control Plane gateway loopback-bound on an unoccupied port such as `127.0.0.1:18080`; do not bind `80` or `443`.
4. On the existing server, preserve the current Nginx as sole owner of `80/443`. A future test subdomain requires a separately approved single include under `/etc/nginx/autodeploy-sites/`, `nginx -t`, graceful reload, and a rollback that removes only that include.
5. Before enabling Docker execution, install an Agent as a separate component, validate enrollment in `dry-run`, then review Docker socket scope, image build isolation and per-project proxy policy.

## Rollback for a future approved installation

Stop and remove only the `autodeploy-*` Compose project and its dedicated configuration/include. Do not stop Docker globally, restart Nginx, remove shared networks, or modify existing project containers.
