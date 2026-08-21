#!/bin/sh
set -eu

# The host key is root-only. Copy it to the Agent tmpfs, then drop root before Java starts.
if [ -r /run/autodeploy/github_deploy_key ]; then
  install -d -m 0700 -o agent -g agent /tmp/.ssh
  install -m 0600 -o agent -g agent /run/autodeploy/github_deploy_key /tmp/.ssh/github_deploy_key
  if [ -r /run/autodeploy/github_known_hosts ]; then
    install -m 0644 -o agent -g agent /run/autodeploy/github_known_hosts /tmp/.ssh/known_hosts
  fi
fi

exec su-exec agent:"${AGENT_DOCKER_GID:-988}" java -jar /app/app.jar
