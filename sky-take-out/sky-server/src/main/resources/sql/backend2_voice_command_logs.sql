-- Backend 2 创新版：语音指令日志表
-- 在 PostgreSQL 业务库中执行：
-- docker exec -i postgres psql -U postgres -d logistics < sql/backend2_voice_command_logs.sql

CREATE TABLE IF NOT EXISTS voice_command_logs (
    id              VARCHAR(64) PRIMARY KEY,
    user_id         VARCHAR(64) NOT NULL,
    recognized_text TEXT,
    intent          VARCHAR(64),
    action_type     VARCHAR(64),
    action_json     JSONB,
    need_confirm    BOOLEAN DEFAULT FALSE,
    confirmed       BOOLEAN DEFAULT FALSE,
    executed        BOOLEAN DEFAULT FALSE,
    reply           TEXT,
    source_page     VARCHAR(64),
    created_at      TIMESTAMPTZ DEFAULT now()
);

CREATE INDEX idx_voice_logs_user    ON voice_command_logs(user_id, created_at DESC);
CREATE INDEX idx_voice_logs_intent  ON voice_command_logs(intent);
CREATE INDEX idx_voice_logs_time    ON voice_command_logs(created_at DESC);
