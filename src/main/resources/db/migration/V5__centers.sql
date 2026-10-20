-- Ko'p markazli tizim: har foydalanuvchi bitta o'quv markaziga tegishli (platforma admini bundan mustasno).
-- Guruhlar, so'z to'plamlari va imtihonlar markazni o'z o'qituvchisi orqali oladi.
CREATE TABLE centers (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name          VARCHAR(255) NOT NULL,
    slug          VARCHAR(50)  NOT NULL UNIQUE,
    logo_url      VARCHAR(500),
    primary_color VARCHAR(7)   NOT NULL DEFAULT '#1E3A5F',
    is_active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- Mavjud ma'lumotlar birinchi markazga o'tadi
INSERT INTO centers (name, slug) VALUES ('Asosiy markaz', 'main');

ALTER TABLE users ADD COLUMN center_id UUID REFERENCES centers(id);
UPDATE users SET center_id = (SELECT id FROM centers WHERE slug = 'main') WHERE role <> 'SUPER_ADMIN';
CREATE INDEX idx_users_center ON users(center_id);

-- Yangi rol: markaz direktori
ALTER TABLE users DROP CONSTRAINT IF EXISTS users_role_check;
ALTER TABLE users ADD CONSTRAINT users_role_check
    CHECK (role IN ('SUPER_ADMIN', 'CENTER_ADMIN', 'TEACHER', 'STUDENT'));
ALTER TABLE users ADD CONSTRAINT users_center_required
    CHECK (role = 'SUPER_ADMIN' OR center_id IS NOT NULL);
