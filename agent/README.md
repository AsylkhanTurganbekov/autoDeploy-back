# AutoDeploy Agent

The Agent runs on a target server and makes outbound HTTPS requests to the Control Plane. It receives a one-time enrollment credential, sends heartbeats, polls for a signed deployment manifest, and rejects invalid or expired manifests.

It does not accept arbitrary shell commands and its Docker executor is intentionally disabled in this revision. Installing an executor, mounting a Docker socket, or changing Nginx on a target server requires a separately reviewed integration plan and operator approval.

Required runtime variables are `CONTROL_PLANE_URL`, `AGENT_SERVER_ID`, `AGENT_CREDENTIAL`, and the same base64 `MANIFEST_SIGNING_KEY` configured in the Control Plane. Enrollment credentials and signing keys must be supplied via the target host's secret manager or protected environment, never committed to Git.

Run locally for compilation only:

```bash
cd agent
mvn test
```
