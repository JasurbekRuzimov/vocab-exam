#!/bin/sh
# Har kuni bazaning zaxira nusxasi; 14 kundan eskilari o'chiriladi.
# O'rnatish: sudo cp deploy/backup.sh /opt/examaker/backup.sh && sudo chmod +x /opt/examaker/backup.sh
# Cron (har kuni 03:00): echo "0 3 * * * postgres /opt/examaker/backup.sh" | sudo tee /etc/cron.d/examaker-backup
set -e
DIR=/var/backups/examaker
mkdir -p "$DIR"
pg_dump -Fc examaker > "$DIR/examaker_$(date +%F).dump"
find "$DIR" -name 'examaker_*.dump' -mtime +14 -delete
