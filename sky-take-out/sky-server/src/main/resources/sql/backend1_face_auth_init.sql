-- Backend 1：人脸登录绑定表与登录日志
-- psql -h localhost -U postgres -d smart_logistics -f src/main/resources/sql/backend1_face_auth_init.sql

CREATE TABLE IF NOT EXISTS user_face_bindings (
    id              VARCHAR(64) PRIMARY KEY,
    user_id         VARCHAR(64) NOT NULL,
    baidu_group_id  VARCHAR(64) NOT NULL,
    baidu_user_id   VARCHAR(64) NOT NULL,
    face_image_url  VARCHAR(255),
    face_image_object_key VARCHAR(255),
    enabled         BOOLEAN DEFAULT TRUE,
    created_at      TIMESTAMPTZ DEFAULT now(),
    updated_at      TIMESTAMPTZ DEFAULT now()
);

ALTER TABLE user_face_bindings
    ADD COLUMN IF NOT EXISTS face_image_url VARCHAR(255);

ALTER TABLE user_face_bindings
    ADD COLUMN IF NOT EXISTS face_image_object_key VARCHAR(255);

CREATE INDEX IF NOT EXISTS idx_face_bindings_user ON user_face_bindings(user_id);
CREATE INDEX IF NOT EXISTS idx_face_bindings_baidu ON user_face_bindings(baidu_group_id, baidu_user_id);

CREATE TABLE IF NOT EXISTS face_login_logs (
    id          VARCHAR(64) PRIMARY KEY,
    user_id     VARCHAR(64),
    confidence  DECIMAL(6,2),
    success     BOOLEAN NOT NULL,
    reason      VARCHAR(128),
    ip          VARCHAR(64),
    device_id   VARCHAR(128),
    created_at  TIMESTAMPTZ DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_face_login_logs_user ON face_login_logs(user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_face_login_logs_time ON face_login_logs(created_at DESC);
