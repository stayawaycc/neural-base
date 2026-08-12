CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE users (
    id            BIGSERIAL PRIMARY KEY,
    username      VARCHAR(50)  NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    role          VARCHAR(20)  NOT NULL DEFAULT 'USER',
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE kb (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    color       VARCHAR(20),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE document (
    id         BIGSERIAL PRIMARY KEY,
    kb_id      BIGINT REFERENCES kb(id) ON DELETE CASCADE,
    title      VARCHAR(255)  NOT NULL,
    file_type  VARCHAR(20),
    file_path  VARCHAR(500),
    source_url VARCHAR(1000),
    status     VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    chunk_count INT          NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE TABLE chat_session (
    id             BIGSERIAL PRIMARY KEY,
    title          VARCHAR(255) NOT NULL,
    mode           VARCHAR(10)  NOT NULL DEFAULT 'NORMAL',
    kb_id          BIGINT REFERENCES kb(id) ON DELETE SET NULL,
    model          VARCHAR(50)  NOT NULL DEFAULT 'deepseek-v4-flash',
    first_question TEXT,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE chat_message (
    id         BIGSERIAL PRIMARY KEY,
    session_id BIGINT      NOT NULL REFERENCES chat_session(id) ON DELETE CASCADE,
    role       VARCHAR(10) NOT NULL,
    content    TEXT        NOT NULL,
    feedback   VARCHAR(10),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE baguwen (
    id         BIGSERIAL PRIMARY KEY,
    source_url VARCHAR(1000),
    title      VARCHAR(255),
    category   VARCHAR(50),
    tags       TEXT[],
    qa_json    JSONB,
    status     VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE crawler_source (
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(100)  NOT NULL,
    url        VARCHAR(1000) NOT NULL,
    type       VARCHAR(10)   NOT NULL,
    selector   VARCHAR(500),
    category   VARCHAR(50),
    enabled    BOOLEAN       NOT NULL DEFAULT TRUE,
    freq_min   INT           NOT NULL DEFAULT 1440,
    last_crawl TIMESTAMPTZ
);

CREATE TABLE daily_news (
    id         BIGSERIAL PRIMARY KEY,
    source_id  BIGINT REFERENCES crawler_source(id) ON DELETE SET NULL,
    url        VARCHAR(1000),
    title      VARCHAR(255) NOT NULL,
    category   VARCHAR(50),
    summary    TEXT,
    is_read    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE crawl_task (
    id         BIGSERIAL PRIMARY KEY,
    type       VARCHAR(20)  NOT NULL,
    target_url VARCHAR(1000),
    status     VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    progress   INT          NOT NULL DEFAULT 0,
    error      TEXT,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Spring AI PgVectorStore 兼容表（4096 维）
CREATE TABLE vector_store (
    id        UUID PRIMARY KEY,
    content   TEXT,
    metadata  JSONB,
    embedding VECTOR(4096)
);

-- 注意：pgvector 0.8.x 的 HNSW/IVFFlat 索引最多支持 2000 维，
-- 而 embedding 为 vector(4096)，故此处无法创建向量索引。
-- 待 embedding 维度 <= 2000 或 pgvector 支持更高维后再补充：
-- CREATE INDEX idx_vector_store_embedding
--     ON vector_store USING hnsw (embedding vector_cosine_ops);
