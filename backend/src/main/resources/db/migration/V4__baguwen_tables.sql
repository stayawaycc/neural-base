-- Phase 4 八股文库
CREATE TABLE baguwen_article (
    id         BIGSERIAL PRIMARY KEY,
    title      VARCHAR(255) NOT NULL,
    url        TEXT,
    content    TEXT NOT NULL,
    tags       JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_baguwen_article_title ON baguwen_article(title);

CREATE TABLE baguwen_import_task (
    id         BIGSERIAL PRIMARY KEY,
    url        TEXT NOT NULL,
    status     VARCHAR(16) NOT NULL,
    error_msg  TEXT,
    article_id BIGINT REFERENCES baguwen_article(id) ON DELETE SET NULL,
    attempt    INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_baguwen_task_status ON baguwen_import_task(status);

CREATE TABLE baguwen_qa_pair (
    id         BIGSERIAL PRIMARY KEY,
    article_id BIGINT NOT NULL REFERENCES baguwen_article(id) ON DELETE CASCADE,
    question   TEXT NOT NULL,
    answer     TEXT NOT NULL,
    difficulty VARCHAR(8) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_baguwen_qa_article ON baguwen_qa_pair(article_id, sort_order);
