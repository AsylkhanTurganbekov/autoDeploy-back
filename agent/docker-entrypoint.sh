#!/bin/sh
set -eu

# The host key is root-only. Copy it to the Agent tmpfs, then drop root before Java starts.
if [ -r /run/autodeploy/github_deploy_key ]; then
  install -d -m 0700 -o agent -g agent /tmp/.ssh
  install -m 0600 -o agent -g agent /run/autodeploy/github_deploy_key /tmp/.ssh/github_deploy_key
fi

# su-exec resolves the named user's groups and would otherwise discard Docker's
# numeric supplementary socket group.  Add that group inside this short-lived
# root-only setup phase, then start Java as the unprivileged Agent user.
if [ -n "${AGENT_DOCKER_GID:-}" ] && echo "$AGENT_DOCKER_GID" | grep -Eq '^[0-9]+$'; then
  socket_group="$(getent group "$AGENT_DOCKER_GID" 2>/dev/null | cut -d: -f1 || true)"
  if [ -z "$socket_group" ]; then
    socket_group="autodeploy-docker"
    addgroup -S -g "$AGENT_DOCKER_GID" "$socket_group"
  fi
  addgroup agent "$socket_group" >/dev/null 2>&1 || true
fi

exec su-exec agent java -jar /app/app.jar
