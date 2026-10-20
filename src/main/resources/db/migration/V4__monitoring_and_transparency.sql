-- Imtihon jarayoni: o'quvchi qachon boshladi, oxirgi faolligi, necha marta boshqa oynaga o'tdi
CREATE TABLE exam_attempts (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    assignment_id    UUID NOT NULL REFERENCES exam_assignments(id) ON DELETE CASCADE,
    student_id       UUID NOT NULL REFERENCES users(id)            ON DELETE CASCADE,
    started_at       TIMESTAMP NOT NULL DEFAULT NOW(),
    last_seen_at     TIMESTAMP NOT NULL DEFAULT NOW(),
    focus_lost_count INT       NOT NULL DEFAULT 0,
    UNIQUE (assignment_id, student_id)
);

-- Xato turi: ARTICLE, TENSE, SPELLING, ... (eski yozuvlarda null)
ALTER TABLE answer_errors ADD COLUMN category VARCHAR(30);

-- Natija e'lon qilingandan keyin ball o'zgarsa: kim, qachon, nimadan nimaga, nima uchun
CREATE TABLE result_changes (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    result_id   UUID NOT NULL REFERENCES exam_results(id) ON DELETE CASCADE,
    changed_by  UUID REFERENCES users(id) ON DELETE SET NULL,
    old_score   INT  NOT NULL,
    old_max     INT  NOT NULL,
    new_score   INT  NOT NULL,
    new_max     INT  NOT NULL,
    reason      TEXT NOT NULL,
    changed_at  TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_result_changes_result ON result_changes(result_id);
