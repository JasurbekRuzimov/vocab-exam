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
| SUPER_ADMIN | Platforma admini: o'quv markazlarini yaratadi, yoqadi/o'chiradi, barcha foydalanuvchilar |
| CENTER_ADMIN | Markaz direktori: o'z markazidagi foydalanuvchi va guruhlar, o'qituvchilar statistikasi |
| TEACHER | O'z guruhlari, so'zlar, imtihonlar, javoblarni tekshirish |
| STUDENT | Imtihon topshirish, natijalarni ko'rish |

## Ko'p markazli tizim
Bitta server bir nechta o'quv markaziga xizmat qiladi. Har foydalanuvchi bitta markazga tegishli
(platforma admini bundan mustasno) va faqat o'z markazi ma'lumotini ko'radi. Guruh, so'z to'plami va
imtihonlar markazni o'z o'qituvchisi orqali oladi. Markazning nomi, logotipi va rangi sahifada ko'rinadi.
O'chirilgan markaz foydalanuvchilari tizimga kira olmaydi. Yangilashda mavjud ma'lumotlar "Asosiy markaz"ga o'tadi.

## Asosiy sahifalar
- `/` — Dashboard
- `/admin/centers` — O'quv markazlari (platforma admini)
- `/admin/users` — Foydalanuvchilar (platforma admini yoki markaz direktori)
- `/admin/groups` — Guruhlar (platforma admini yoki markaz direktori)
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
