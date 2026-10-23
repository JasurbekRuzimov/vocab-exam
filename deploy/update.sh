#!/bin/sh
# Yangi versiyani serverga chiqarish: kodni oladi, yig'adi, ilovani qayta ishga tushiradi.
# Ishlatish (loyiha papkasida): sudo ./deploy/update.sh
set -e
git pull --ff-only
mvn -B -q package -DskipTests
cp target/examaker-1.0.0.jar /opt/examaker/examaker.jar
chown examaker:examaker /opt/examaker/examaker.jar
systemctl restart examaker
echo "Yangilandi. Holat: sudo systemctl status examaker"
