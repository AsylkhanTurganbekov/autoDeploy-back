# Agent deployment pipeline

## User flow

1. The user adds a server and enrolls its outbound-only Agent once.
2. In **New project**, the user supplies a GitHub HTTPS/SSH URL, branch and an ONLINE server.
3. The API records a queued deployment and sends the Agent only an expiring, HMAC-signed manifest. The manifest has no shell command, secret, Docker socket path or proxy configuration.
4. The Agent clones the selected branch, statically scans at most three levels for supported services, and reports the service plan to the API.
5. A single public candidate is selected automatically. A multi-service repository is shown in the UI; the user can select a public service before the next deployment. Private services are not published.
6. A repository Dockerfile is policy-checked. If absent, the Agent writes a reviewed runtime template in its temporary checkout only.
7. The Agent chooses a free port in `18100–18999`, builds an image, creates/uses an AutoDeploy-labelled Docker network, starts a resource-limited container, runs an internal health check, and posts sanitized logs/status over the outbound control-plane connection.
8. The browser obtains status and logs through authenticated SSE. A failed health check removes the attempted container and restores the prior AutoDeploy-managed image when one exists.

## Supported static detections

| Evidence | Runtime | Initial template |
| --- | --- | --- |
| `package.json` | Node.js | Next/Node multi-stage |
| `pom.xml`, `build.gradle*` | Spring Boot | Maven → JRE |
| `requirements.txt`, `pyproject.toml` | Python | Python/Gunicorn |
| `go.mod` | Go | Go → distroless |
| `*.csproj` | .NET | detected; template intentionally blocked until reviewed |
| `Dockerfile` | Dockerfile | repository Dockerfile after policy checks |

## LLM boundary

`DeploymentPlanner` calls a NITEC/OpenAI-compatible chat completion endpoint only if `AI_ADVISOR_URL` and `AI_ADVISOR_API_KEY` are present in the API process environment. It receives repository identifier without credentials, branch, static service metadata and the allowed port range. It returns JSON with at most one public service key and a short summary. The result is validated against saved services; invalid, unavailable or malformed LLM responses fall back to deterministic policy.

The LLM never receives environment variables, GitHub/SSH credentials, server credentials, Docker socket access, raw file contents or logs with secrets. Its output cannot become a command, a Dockerfile, a port binding, an Nginx configuration or an Agent manifest.
