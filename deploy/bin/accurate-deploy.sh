#!/usr/bin/env bash
# Deploys ONE Accurate Powertech image and waits until it is healthy; if it does not become healthy the previous image
# is started again and the exit code is 1. Called by .github/workflows/release.yml over SSH, and usable by hand:
#   bin/accurate-deploy.sh backend|frontend <release>     release = full git SHA of the image tag, or "latest"
# As an SSH forced command (authorized_keys: command="/home/occpl/accurate/bin/accurate-deploy.sh",restrict ssh-ed25519 ...)
# the arguments come from the client's command line, so that key can do nothing else on this server.
# Same shape as /opt/occpl/bin/courier-deploy.sh.
set -euo pipefail
cd "$(dirname "$0")/.."
exec > >(tee -a deploy.log 2>/dev/null || cat) 2>&1

if [ -n "${SSH_ORIGINAL_COMMAND:-}" ]; then
  read -r -a original <<<"$SSH_ORIGINAL_COMMAND"
  set -- "${original[@]}"
fi
usage() { echo "usage: accurate-deploy.sh backend|frontend <40-character git SHA | latest>" >&2; exit 2; }
[ $# -eq 2 ] || usage
part=$1
release=$2
[[ "$release" =~ ^[0-9a-f]{40}$ || "$release" == latest ]] || usage
case "$part" in
  backend) service=backend; var=BACKEND_RELEASE ;;
  frontend) service=frontend; var=FRONTEND_RELEASE ;;
  *) usage ;;
esac
[ -f .env ] || { echo "no .env: run bin/accurate-setup.sh first" >&2; exit 1; }
log() { echo "$(date -u +%FT%TZ) $*"; }

current() { sed -n "s/^$var=//p" .env | tail -1; }
set_release() {
  if grep -q "^$var=" .env; then sed -i "s/^$var=.*/$var=$1/" .env; else printf '%s=%s\n' "$var" "$1" >> .env; fi
}
previous=$(current)
previous=${previous:-latest}

wait_healthy() { # seconds
  local deadline=$((SECONDS + $1)) id status
  while [ $SECONDS -lt $deadline ]; do
    id=$(docker compose ps -q "$service")
    status=$([ -n "$id" ] && docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$id" || echo missing)
    if [ "$status" = healthy ]; then echo "$service $status"; return 0; fi
    sleep 5
  done
  echo "$service not healthy after $1 s"; docker compose ps "$service" || true; docker compose logs --tail 60 "$service" || true
  return 1
}

start() { # release
  set_release "$1"
  # No errexit inside a function used as a condition: every step needs an explicit check.
  docker compose pull --quiet "$service" || return 1
  docker compose up -d "$service" || return 1
  wait_healthy 240 || return 1
  if [ "$part" = backend ]; then
    # The frontend's nginx looks the backend up once at start; after the backend was recreated it would keep
    # sending requests to the old address.
    docker compose restart frontend || return 1
  fi
}

log "deploying accurate $part $release (running: $previous)"
if start "$release"; then
  log "accurate $part $release running"
  docker image prune -f >/dev/null || true
  exit 0
fi
log "deploy of accurate $part $release FAILED"
if [ "$previous" != "$release" ]; then
  log "rolling back to $previous"
  if start "$previous"; then log "back on $previous"; else log "ROLLBACK FAILED: investigate with docker compose logs"; fi
fi
exit 1
