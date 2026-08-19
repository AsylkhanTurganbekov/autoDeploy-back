# Existing server integration plan — approval required

This document changes nothing. First perform a read-only audit of ports, Docker, Nginx includes and service managers. Install the Agent only as a new isolated Compose project. The existing Nginx remains owner of 80/443. An AutoDeploy Nginx include and project configs require separate approval, `nginx -t`, graceful reload and a rollback that touches only the AutoDeploy config.
