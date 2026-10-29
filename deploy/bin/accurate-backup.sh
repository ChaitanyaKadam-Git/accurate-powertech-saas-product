#!/usr/bin/env bash
# Nightly backup of Accurate Powertech on the OCCPL dev server: the Postgres database (pg_dump, custom format) and the
# uploaded photos (the "uploads" volume). Local copies in ~/accurate/backups, kept KEEP_DAYS days (default 7). With
# BACKUP_S3_URI in .env each run is also copied there (the instance role gives access; no aws configure).
#   bin/accurate-backup.sh            run a backup now
#   bin/accurate-backup.sh restore <db-file.dump> [uploads-file.tgz]   see deploy/README.md before using
# Scheduled from the occpl user's crontab (deploy/README.md):  15 21 * * * $HOME/accurate/bin/accurate-backup.sh
set -euo pipefail
cd "$(dirname "$0")/.."
exec >> backups.log 2>&1
envval() { sed -n "s/^$1=//p" .env | tail -1; }
dir=${BACKUP_DIR:-$PWD/backups}
keep=$(envval KEEP_DAYS); keep=${keep:-7}
s3=$(envval BACKUP_S3_URI)
log() { echo "$(date -u +%FT%TZ) $*"; }
mkdir -p "$dir" && chmod 700 "$dir"
exec 9>/tmp/accurate-backup.lock
flock -n 9 || { log "another backup is running"; exit 0; }

if [ "${1:-}" = restore ]; then
  db=${2:?restore needs the .dump file}; up=${3:-}
  log "RESTORE from $db ${up:+and $up}"
  docker compose stop backend
  docker compose exec -T db pg_restore -U quotes -d quotes --clean --if-exists --no-owner < "$db"
  if [ -n "$up" ]; then
    docker run --rm -v accurate_uploads:/u -v "$(cd "$(dirname "$up")" && pwd)":/b:ro alpine sh -c "rm -rf /u/* && tar xzf /b/$(basename "$up") -C /u"
  fi
  docker compose start backend
  log "restore done"
  exit 0
fi

stamp=$(date -u +%Y%m%d-%H%M)
db="$dir/db-$stamp.dump" up="$dir/uploads-$stamp.tgz"
docker compose exec -T db pg_dump -U quotes -d quotes -Fc > "$db.part" && mv "$db.part" "$db"
docker run --rm -v accurate_uploads:/u:ro alpine tar czf - -C /u . > "$up.part" && mv "$up.part" "$up"
chmod 600 "$db" "$up"
log "backup ok: $(basename "$db") $(du -h "$db" | cut -f1), $(basename "$up") $(du -h "$up" | cut -f1)"

find "$dir" -maxdepth 1 -type f \( -name 'db-*.dump' -o -name 'uploads-*.tgz' \) -mtime +"$keep" -delete
if [ -n "$s3" ]; then
  aws s3 sync "$dir" "$s3" --only-show-errors --no-progress --exclude '*.part' && log "copied to $s3"
fi
