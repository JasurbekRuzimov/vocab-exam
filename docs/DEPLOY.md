# VocabExam'ni serverga chiqarish

Bu yo'riqnoma ilovani Docker'siz, Ubuntu serverda **nginx + HTTPS** ortida ishga tushiradi.
Kerakli fayllar `deploy/` papkasida tayyor turibdi.

```
Internet ──HTTPS──▶ nginx (443) ──▶ Spring Boot (127.0.0.1:8080) ──▶ PostgreSQL (localhost)
```

## 0. Nima kerak

| | Tavsiya |
|---|---|
| Server (VPS) | Ubuntu 24.04, 2 vCPU, 2–4 GB RAM, 40 GB disk |
| Domen | masalan `vocabexam.uz` (istalgan .uz registratori) |
| Vaqt | birinchi marta ~1 soat |

> **Muhim (O'zbekiston qonunchiligi).** "Shaxsga doir ma'lumotlar to'g'risida"gi qonunga ko'ra
> O'zbekiston fuqarolarining shaxsiy ma'lumotlari (o'quvchilar ismi, natijalari) O'zbekiston
> hududidagi serverlarda saqlanishi kerak. O'quv markazlariga sotishda mahalliy hostingni
> tanlang (mahalliy data-markazdagi VPS). Sinov uchun istalgan VPS bo'laveradi.

## 1. Serverni tayyorlash

```bash
sudo apt update && sudo apt upgrade -y
sudo apt install -y openjdk-21-jdk maven postgresql nginx certbot python3-certbot-nginx git
sudo ufw allow OpenSSH && sudo ufw allow 'Nginx Full' && sudo ufw enable
```

## 2. Ma'lumotlar bazasi

```bash
sudo -u postgres psql <<'SQL'
CREATE USER vocabexam WITH PASSWORD 'KUCHLI-PAROL';
CREATE DATABASE vocab_exam OWNER vocabexam;
SQL
```

Jadvallarni ilova birinchi ishga tushganda o'zi yaratadi (Flyway).

## 3. Ilova uchun foydalanuvchi va papkalar

```bash
sudo useradd --system --home /opt/vocabexam --shell /usr/sbin/nologin vocabexam
sudo mkdir -p /opt/vocabexam/uploads /opt/vocabexam/pdfs /var/log/vocabexam /etc/vocabexam
sudo chown -R vocabexam:vocabexam /opt/vocabexam /var/log/vocabexam
```

## 4. Kodni olish va yig'ish

```bash
cd /srv && sudo git clone https://github.com/JasurbekRuzimov/vocab-exam.git && cd vocab-exam
sudo mvn -B package -DskipTests
sudo cp target/vocabexam-1.0.0.jar /opt/vocabexam/vocabexam.jar
sudo chown vocabexam:vocabexam /opt/vocabexam/vocabexam.jar
```

## 5. Maxfiy sozlamalar

```bash
sudo cp deploy/vocabexam.env.example /etc/vocabexam/vocabexam.env
sudo nano /etc/vocabexam/vocabexam.env     # DB_PASSWORD va ADMIN_PASSWORD ni yozing
sudo chmod 600 /etc/vocabexam/vocabexam.env
```

`SPRING_PROFILES_ACTIVE=prod` yoqilgan bo'ladi. Bu profil shablonlarni keshlaydi, cookie'larni
faqat HTTPS orqali yuboradi va ilovani faqat `127.0.0.1` da tinglatadi (tashqaridan faqat nginx orqali).

## 6. Ilovani xizmat sifatida ishga tushirish

```bash
sudo cp deploy/vocabexam.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now vocabexam
sudo systemctl status vocabexam          # "active (running)" bo'lishi kerak
sudo tail -f /var/log/vocabexam/app.log  # "Started VocabExamApplication" chiqishini kuting
```

Server qayta yoqilganda ilova o'zi ishga tushadi, yiqilsa 10 soniyada qayta ko'tariladi.

## 7. Domenni serverga ulash

Domen registratorining DNS bo'limida ikkita **A** yozuv qo'shing:

| Nom | Turi | Qiymat |
|---|---|---|
| `@` | A | server IP manzili |
| `www` | A | server IP manzili |

DNS 5 daqiqadan bir necha soatgacha tarqaladi. Tekshirish: `ping vocabexam.uz`.

## 8. nginx va bepul HTTPS sertifikati

```bash
sudo cp deploy/nginx-vocabexam.conf /etc/nginx/sites-available/vocabexam
sudo sed -i 's/DOMEN.UZ/vocabexam.uz/g' /etc/nginx/sites-available/vocabexam   # o'z domeningiz
sudo ln -s /etc/nginx/sites-available/vocabexam /etc/nginx/sites-enabled/
sudo rm -f /etc/nginx/sites-enabled/default
sudo nginx -t && sudo systemctl reload nginx
sudo certbot --nginx -d vocabexam.uz -d www.vocabexam.uz   # HTTP→HTTPS yo'naltirishni tanlang
```

Sertifikat har 90 kunda avtomatik yangilanadi.

## 9. Birinchi kirish

1. `https://vocabexam.uz` ni oching, `admin` va `ADMIN_PASSWORD` bilan kiring.
2. **Markazlar** bo'limida birinchi o'quv markazini va uning direktorini yarating.
3. Direktor o'qituvchi va o'quvchilarni qo'shadi.

## 10. Kunlik zaxira nusxa

```bash
sudo mkdir -p /var/backups/vocabexam && sudo chown postgres /var/backups/vocabexam
sudo cp deploy/backup.sh /opt/vocabexam/backup.sh && sudo chmod +x /opt/vocabexam/backup.sh
echo "0 3 * * * postgres /opt/vocabexam/backup.sh" | sudo tee /etc/cron.d/vocabexam-backup
```

Har kuni soat 03:00 da nusxa olinadi, 14 kundan eskilari o'chadi. Vaqti-vaqti bilan nusxalarni
boshqa joyga ham ko'chirib turing. Tiklash: `sudo -u postgres pg_restore -d vocab_exam --clean FAYL.dump`.

## 11. Yangi versiyani chiqarish

```bash
cd /srv/vocab-exam && sudo ./deploy/update.sh
```

Skript kodni oladi, yig'adi va ilovani qayta ishga tushiradi (~1 daqiqa uzilish).
Yangi baza o'zgarishlari (migratsiyalar) avtomatik qo'llanadi.

## Muammo bo'lsa

| Belgi | Nima qilish kerak |
|---|---|
| Sayt ochilmaydi (502) | `sudo systemctl status vocabexam` va `/var/log/vocabexam/app.log` ni ko'ring |
| Login qilgach yana login sahifasi chiqadi | Sayt `https://` orqali ochilganini tekshiring: prod profilda cookie faqat HTTPS bilan ishlaydi |
| Fayl yuklashda 413 xato | nginx'dagi `client_max_body_size` ni oshiring |
| Bazaga ulanmaydi | `/etc/vocabexam/vocabexam.env` dagi `DB_PASSWORD` 2-qadamdagi parol bilan bir xilmi |

## Sotuvdan oldin

- [ ] Git tarixida qolgan eski baza parolini hech qayerda ishlatmang (yangi parol qo'ying)
- [ ] `ADMIN_PASSWORD` kuchli va faqat sizda
- [ ] Zaxira nusxadan tiklashni bir marta sinab ko'ring
- [ ] Foydalanish shartlari va maxfiylik siyosati sahifasini tayyorlang
