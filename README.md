# VocabExam — Ingliz tili o'quv markazi uchun Vocabulary Exam tizimi

## Texnologiyalar
- Java 21, Spring Boot 3.3.0
- PostgreSQL 17, Flyway migrations
- Thymeleaf + Bootstrap 5
- iText 7 (PDF generatsiya)
- Apache POI (DOCX fayl o'qish)
- Docker + Docker Compose

## Tezkor ishga tushurish

### 1. `.env` fayl yarating
```bash
cp .env.example .env
# .env faylni tahrirlang
```

### 2. Docker orqali
```bash
docker-compose up -d
```
Brauzerda: http://localhost:8080

### 3. Mahalliy ishga tushurish
```bash
# PostgreSQL 17 o'rnatilgan bo'lishi kerak
mvn spring-boot:run
```

## Birinchi admin
- **Login:** `ADMIN_USERNAME` (standart: `admin`)
- **Parol:** `ADMIN_PASSWORD` muhit o'zgaruvchisi. Berilmasa, birinchi ishga tushishda
  tasodifiy parol yaratiladi va logga **bir marta** chiqariladi — kirgandan keyin almashtiring.

## Rollar
| Rol | Vakolat |
|-----|---------|
| SUPER_ADMIN | Barcha foydalanuvchilar, guruhlar, statistika |
| TEACHER | O'z guruhlari, so'zlar, imtihonlar, javoblarni tekshirish |
| STUDENT | Imtihon topshirish, natijalarni ko'rish |

## Asosiy sahifalar
- `/` — Dashboard
- `/admin/users` — Foydalanuvchilar (Admin)
- `/admin/groups` — Guruhlar (Admin)
- `/teacher/word-sets` — So'zlar to'plamlari
- `/teacher/exams` — Imtihonlar
- `/teacher/exams/{assignmentId}/submissions` — Jonli monitoring va statistika (har 10 soniyada yangilanadi)
- `/student/exams` — Imtihon topshirish
- `/student/results` — Natijalar

## API Endpoints
- `POST /api/exams` — Imtihon yaratish + PDF generatsiya
- `GET /api/exams/{id}/download-zip` — Barcha variantlarni ZIP yuklab olish
- `PUT /api/online-exams/{assignmentId}/answers/{wordId}` — Avtosaqlash
- `POST /api/online-exams/{assignmentId}/submit` — Topshirish
- `POST /api/online-exams/{assignmentId}/activity` — Monitoring signali (`ping` yoki `focus-lost`)
- `POST /api/answers/{answerId}/errors` — Xato belgilash (`category`: SPELLING, ARTICLE, TENSE, WORD_USAGE, WORD_ORDER, GRAMMAR, PUNCTUATION, OTHER)
- `POST /api/online-exams/{assignmentId}/submissions/{studentId}/result` — Natija; e'lon qilingan ball o'zgarsa `changeReason` majburiy
