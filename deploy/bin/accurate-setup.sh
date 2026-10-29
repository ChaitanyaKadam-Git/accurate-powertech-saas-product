#!/usr/bin/env bash
# One-time setup on the OCCPL dev server, run as user occpl from /home/occpl/accurate:
#   bin/accurate-setup.sh accurate.occplsoftech.com ghcr.io/<owner>/<repo>
# Writes .env (keeps values that are already there, generates the database password and JWT secret) and
# installs the Caddy site block. Safe to run again.
set -euo pipefail
cd "$(dirname "$0")/.."
[ $# -eq 2 ] || { echo "usage: accurate-setup.sh <host> <image prefix, e.g. ghcr.io/owner/repo>" >&2; exit 2; }
host=$1
prefix=$2

touch .env && chmod 600 .env
setdefault() { grep -q "^$1=" .env || printf '%s=%s\n' "$1" "$2" >> .env; }
setdefault IMAGE_PREFIX "$prefix"
setdefault DB_PASS "$(openssl rand -hex 24)"
setdefault JWT_SECRET "$(openssl rand -hex 48)"
setdefault CORS_ORIGINS "https://$host"
setdefault PROXY_HOPS 2
setdefault BACKEND_RELEASE latest
setdefault FRONTEND_RELEASE latest

# The platform's Caddy also serves courier and the OCCPL hosts: a broken extra file would stop it from starting on the
# next OCCPL deploy, so the file stays only when the whole config validates. A reload is atomic (a bad config is refused
# and the running one is kept).
site=/opt/occpl/extra/accurate.caddy
backup=$(mktemp)
[ -f "$site" ] && cp "$site" "$backup" || : > "$backup"
sed "s/^accurate\.occplsoftech\.com {/$host {/" accurate.caddy > "$site"
if ! docker exec occpl-caddy-1 caddy validate --config /etc/caddy/Caddyfile --adapter caddyfile; then
  if [ -s "$backup" ]; then cp "$backup" "$site"; else rm -f "$site"; fi
  rm -f "$backup"
  echo "Caddy config does not validate; $site restored, nothing reloaded" >&2
  exit 1
fi
rm -f "$backup"
docker exec occpl-caddy-1 caddy reload --config /etc/caddy/Caddyfile --adapter caddyfile
echo "setup done; start with: docker compose pull && docker compose up -d"
