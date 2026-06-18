-- ── USERS ─────────────────────────────────────────────────────────────────────
CREATE TABLE users (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    full_name     VARCHAR(255) NOT NULL,
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role          VARCHAR(20)  NOT NULL CHECK (role IN ('SUPER_ADMIN','TEACHER','STUDENT')),
    is_active     BOOLEAN      NOT NULL DEFAULT TRUE,
    login_attempts INT         NOT NULL DEFAULT 0,
    locked_until  TIMESTAMP,
    created_at    TIMESTAMP    NOT NULL DEFAULT NOW(),
    last_login    TIMESTAMP
);

CREATE TABLE groups (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name       VARCHAR(255) NOT NULL,
    teacher_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE group_students (
    group_id   UUID NOT NULL REFERENCES groups(id)  ON DELETE CASCADE,
    student_id UUID NOT NULL REFERENCES users(id)   ON DELETE CASCADE,
    PRIMARY KEY (group_id, student_id)
);

CREATE TABLE word_sets (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name       VARCHAR(255) NOT NULL,
    teacher_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    file_name  VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE words (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    word_set_id UUID NOT NULL REFERENCES word_sets(id) ON DELETE CASCADE,
    text        VARCHAR(255) NOT NULL
);

CREATE TABLE exams (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title          VARCHAR(255) NOT NULL,
    type           VARCHAR(10)  NOT NULL CHECK (type IN ('PDF','ONLINE')),
    word_set_id    UUID NOT NULL REFERENCES word_sets(id) ON DELETE RESTRICT,
    teacher_id     UUID NOT NULL REFERENCES users(id)     ON DELETE RESTRICT,
    question_count INT  NOT NULL,
    variant_count  INT  NOT NULL DEFAULT 1,
    status         VARCHAR(20)  NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT','GENERATED')),
    created_at     TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE TABLE exam_variants (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    exam_id        UUID NOT NULL REFERENCES exams(id) ON DELETE CASCADE,
    variant_number INT  NOT NULL,
    pdf_path       VARCHAR(500)
);

CREATE TABLE exam_variant_words (
    variant_id UUID NOT NULL REFERENCES exam_variants(id) ON DELETE CASCADE,
    word_id    UUID NOT NULL REFERENCES words(id)          ON DELETE CASCADE,
    position   INT  NOT NULL,
    PRIMARY KEY (variant_id, word_id)
);

CREATE TABLE exam_assignments (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    exam_id     UUID NOT NULL REFERENCES exams(id)  ON DELETE RESTRICT,
    target_type VARCHAR(10) NOT NULL CHECK (target_type IN ('GROUP','STUDENT')),
    group_id    UUID REFERENCES groups(id)  ON DELETE SET NULL,
    student_id  UUID REFERENCES users(id)   ON DELETE SET NULL,
    deadline    TIMESTAMP,
    status      VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','CANCELLED','COMPLETED')),
    assigned_at TIMESTAMP   NOT NULL DEFAULT NOW()
);

CREATE TABLE online_answers (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    assignment_id UUID NOT NULL REFERENCES exam_assignments(id) ON DELETE CASCADE,
    student_id    UUID NOT NULL REFERENCES users(id)            ON DELETE CASCADE,
    word_id       UUID NOT NULL REFERENCES words(id)            ON DELETE CASCADE,
    answer_text   TEXT,
    score         INT,
    is_submitted  BOOLEAN   NOT NULL DEFAULT FALSE,
    submitted_at  TIMESTAMP,
    saved_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (assignment_id, student_id, word_id)
);

CREATE TABLE answer_errors (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    answer_id    UUID NOT NULL REFERENCES online_answers(id) ON DELETE CASCADE,
    error_start  INT  NOT NULL,
    error_end    INT  NOT NULL,
    comment      TEXT NOT NULL,
    correct_hint VARCHAR(500),
    created_at   TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE exam_results (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    assignment_id UUID NOT NULL REFERENCES exam_assignments(id) ON DELETE CASCADE,
    student_id    UUID NOT NULL REFERENCES users(id)            ON DELETE CASCADE,
    total_score   INT  NOT NULL DEFAULT 0,
    max_score     INT  NOT NULL DEFAULT 100,
    comment       TEXT,
    checked_at    TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (assignment_id, student_id)
);

CREATE TABLE notifications (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type       VARCHAR(50) NOT NULL,
    message    TEXT        NOT NULL,
    link       VARCHAR(500),
    is_read    BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP   NOT NULL DEFAULT NOW()
);

CREATE TABLE audit_logs (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID REFERENCES users(id) ON DELETE SET NULL,
    action     VARCHAR(100) NOT NULL,
    target     VARCHAR(255),
    details    TEXT,
    ip         VARCHAR(50),
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- INDEXES
CREATE INDEX idx_users_email         ON users(email);
CREATE INDEX idx_words_wordset       ON words(word_set_id);
CREATE INDEX idx_exams_teacher       ON exams(teacher_id);
CREATE INDEX idx_assignments_exam    ON exam_assignments(exam_id);
CREATE INDEX idx_assignments_student ON exam_assignments(student_id);
CREATE INDEX idx_answers_assignment  ON online_answers(assignment_id);
CREATE INDEX idx_answers_student     ON online_answers(student_id);
CREATE INDEX idx_errors_answer       ON answer_errors(answer_id);
CREATE INDEX idx_results_student     ON exam_results(student_id);
CREATE INDEX idx_notifications_user  ON notifications(user_id, is_read);

-- DEFAULT SUPER ADMIN (parol: Admin@1234 — DataInitializer tomonidan hash to'g'rilanadi)
INSERT INTO users (full_name, email, password_hash, role, is_active)
VALUES ('Super Admin', 'admin@vocabexam.uz', 'PLACEHOLDER_REPLACED_BY_APP', 'SUPER_ADMIN', TRUE)
ON CONFLICT (email) DO NOTHING;
