# AutoDeploy Agent

The Agent runs on a target server and makes outbound HTTPS requests to the Control Plane. It receives a one-time enrollment credential, sends heartbeats, polls for a signed deployment manifest, and rejects invalid or expired manifests.

It does not accept arbitrary shell commands. The default executor is `dry-run`; `AGENT_EXECUTION_MODE=docker` is an explicit opt-in and uses fixed Docker arguments with resource and privilege restrictions. Mounting a Docker socket or changing Nginx on a target server requires a separately reviewed integration plan and operator approval.

For first registration set `CONTROL_PLANE_URL`, `AGENT_ENROLLMENT_TOKEN`, and the same base64 `MANIFEST_SIGNING_KEY` configured in the Control Plane. The Agent exchanges the one-time token for its own credential and persists that identity in `AGENT_IDENTITY_PATH` with owner-only permissions. On later starts it uses that persisted identity; enrollment tokens must be delivered through a protected runtime secret and never committed to Git.

`AGENT_SERVER_ID` and `AGENT_CREDENTIAL` remain supported for an operator-managed identity, but must also come from protected runtime secret storage.

Run locally for compilation only:

```bash
cd agent
mvn test
```
