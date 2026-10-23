#!/bin/sh
# Har kuni bazaning zaxira nusxasi; 14 kundan eskilari o'chiriladi.
# O'rnatish: sudo cp deploy/backup.sh /opt/vocabexam/backup.sh && sudo chmod +x /opt/vocabexam/backup.sh
# Cron (har kuni 03:00): echo "0 3 * * * postgres /opt/vocabexam/backup.sh" | sudo tee /etc/cron.d/vocabexam-backup
set -e
DIR=/var/backups/vocabexam
mkdir -p "$DIR"
pg_dump -Fc vocab_exam > "$DIR/vocab_exam_$(date +%F).dump"
find "$DIR" -name 'vocab_exam_*.dump' -mtime +14 -delete
