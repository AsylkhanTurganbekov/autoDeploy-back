# Architecture decision record

The control plane uses Next.js, Java 21/Spring Boot, PostgreSQL and a separate Spring worker. The API owns persistent users, projects, encrypted secrets, GitHub connections, deployments, logs, webhook delivery IDs and jobs. The worker atomically claims PostgreSQL jobs with `FOR UPDATE SKIP LOCKED`; this keeps job history observable and avoids a second broker dependency in the first release.

The browser calls the API with short-lived JWT bearer tokens. Project ownership is enforced on every project and deployment route. Deployment status and logs are durable database records; the API exposes them as authenticated Server-Sent Events and cancels the emitter task when a client disconnects.

GitHub PATs are verified against GitHub before saving and AES-GCM encrypted at rest using a non-database `SECRETS_MASTER_KEY`. Webhook input has an HMAC SHA-256 signature check and delivery-ID idempotency. Tokens, decrypted env values and webhook signatures are never returned or logged.

An eventual server agent is a separate deployment boundary. It must verify a signed allowlisted manifest and reject privileged mode, host networking, host mounts, published application ports and Docker Compose supplied by a user repository.
