#!/bin/sh
# Yangi versiyani serverga chiqarish: kodni oladi, yig'adi, ilovani qayta ishga tushiradi.
# Ishlatish (loyiha papkasida): sudo ./deploy/update.sh
set -e
git pull --ff-only
mvn -B -q package -DskipTests
cp target/vocabexam-1.0.0.jar /opt/vocabexam/vocabexam.jar
chown vocabexam:vocabexam /opt/vocabexam/vocabexam.jar
systemctl restart vocabexam
echo "Yangilandi. Holat: sudo systemctl status vocabexam"
