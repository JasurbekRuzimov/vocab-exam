-- So'z kartochkasi: tarjima va misol gap
ALTER TABLE words ADD COLUMN translation VARCHAR(255);
ALTER TABLE words ADD COLUMN example     TEXT;

-- Imtihondagi savol turlari (vergul bilan): SENTENCE, TRANSLATE_EN_UZ, TRANSLATE_UZ_EN, MULTIPLE_CHOICE
ALTER TABLE exams ADD COLUMN question_types VARCHAR(200) NOT NULL DEFAULT 'SENTENCE';

-- Har javob qaysi turdagi savolga berilgani va avtomatik tekshiruv natijasi
ALTER TABLE online_answers ADD COLUMN question_type VARCHAR(30) NOT NULL DEFAULT 'SENTENCE';
ALTER TABLE online_answers ADD COLUMN is_correct    BOOLEAN;
