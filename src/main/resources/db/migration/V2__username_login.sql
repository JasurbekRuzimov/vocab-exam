-- Kirish endi email emas, oddiy login (username) orqali. Email ixtiyoriy bo'ladi.
ALTER TABLE users ADD COLUMN username VARCHAR(50);

-- Mavjud foydalanuvchilarga login: emailning @ dan oldingi qismi (masalan ali@gmail.com -> ali).
-- Bir xil bo'lib qolsa, raqam qo'shiladi: ali, ali2, ali3 ...
WITH base AS (
    SELECT id,
           lower(regexp_replace(split_part(email, '@', 1), '[^a-zA-Z0-9._-]', '', 'g')) AS b,
           row_number() OVER (
               PARTITION BY lower(regexp_replace(split_part(email, '@', 1), '[^a-zA-Z0-9._-]', '', 'g'))
               ORDER BY created_at, id) AS rn
    FROM users
)
UPDATE users u
SET username = CASE WHEN base.rn = 1 THEN base.b ELSE base.b || base.rn END
FROM base
WHERE u.id = base.id;

-- Juda qisqa yoki bo'sh chiqqanlar uchun zaxira login
UPDATE users
SET username = 'user' || substr(replace(id::text, '-', ''), 1, 8)
WHERE username IS NULL OR length(username) < 3;

ALTER TABLE users ALTER COLUMN username SET NOT NULL;
ALTER TABLE users ADD CONSTRAINT uq_users_username UNIQUE (username);
ALTER TABLE users ALTER COLUMN email DROP NOT NULL;
