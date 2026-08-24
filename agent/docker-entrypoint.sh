#!/bin/sh
set -eu

# The Docker socket keeps the host's numeric group.  Add the unprivileged Agent
# user to a matching group at startup; the socket itself remains the only host
# capability mounted into this container.
if [ -S /var/run/docker.sock ]; then
  docker_gid="$(stat -c '%g' /var/run/docker.sock)"
  docker_group="$(awk -F: -v gid="$docker_gid" '$3 == gid { print $1; exit }' /etc/group)"
  if [ -z "$docker_group" ]; then
    docker_group="dockerhost"
    addgroup -g "$docker_gid" "$docker_group"
  fi
  addgroup agent "$docker_group" >/dev/null 2>&1 || true
fi

# The host key is root-only. Copy it to the Agent tmpfs, then drop root before Java starts.
if [ -r /run/autodeploy/github_deploy_key ]; then
  install -d -m 0700 -o agent -g agent /tmp/.ssh
  install -m 0600 -o agent -g agent /run/autodeploy/github_deploy_key /tmp/.ssh/github_deploy_key
fi

exec su-exec agent java -jar /app/app.jar
