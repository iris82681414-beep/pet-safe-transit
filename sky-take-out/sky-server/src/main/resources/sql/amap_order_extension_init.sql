-- 高德 API 与订单扩展流程配套表
-- 可重复执行：只创建缺失对象，不清空现有业务数据。

CREATE TABLE IF NOT EXISTS address_change_requests (
    id BIGSERIAL PRIMARY KEY,
    request_id VARCHAR(40) UNIQUE NOT NULL,
    order_id VARCHAR(64) NOT NULL,
    customer_id VARCHAR(40) NOT NULL,
    old_address TEXT NOT NULL,
    old_lng DOUBLE PRECISION,
    old_lat DOUBLE PRECISION,
    new_address TEXT NOT NULL,
    new_lng DOUBLE PRECISION NOT NULL,
    new_lat DOUBLE PRECISION NOT NULL,
    contact_name VARCHAR(50),
    contact_phone VARCHAR(30),
    reason TEXT,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING_REVIEW',
    impact_level VARCHAR(30),
    extra_distance_km NUMERIC(10,2),
    estimated_delay_minutes INT,
    extra_cost NUMERIC(10,2),
    need_dispatcher_review BOOLEAN DEFAULT TRUE,
    need_driver_confirm BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMPTZ DEFAULT now(),
    updated_at TIMESTAMPTZ DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_address_change_order_time
    ON address_change_requests(order_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_address_change_status
    ON address_change_requests(status);

CREATE TABLE IF NOT EXISTS address_change_logs (
    id BIGSERIAL PRIMARY KEY,
    request_id VARCHAR(40) NOT NULL,
    operator_id VARCHAR(40),
    operator_role VARCHAR(30),
    action VARCHAR(50) NOT NULL,
    remark TEXT,
    created_at TIMESTAMPTZ DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_address_change_logs_request_time
    ON address_change_logs(request_id, created_at ASC);

CREATE TABLE IF NOT EXISTS driver_ratings (
    id BIGSERIAL PRIMARY KEY,
    rating_id VARCHAR(40) UNIQUE NOT NULL,
    order_id VARCHAR(64) NOT NULL,
    customer_id VARCHAR(40) NOT NULL,
    driver_id VARCHAR(40) NOT NULL,
    plate VARCHAR(30),
    score INT NOT NULL CHECK (score BETWEEN 1 AND 5),
    punctuality INT CHECK (punctuality BETWEEN 1 AND 5),
    service_attitude INT CHECK (service_attitude BETWEEN 1 AND 5),
    cargo_integrity INT CHECK (cargo_integrity BETWEEN 1 AND 5),
    communication INT CHECK (communication BETWEEN 1 AND 5),
    comment TEXT,
    created_at TIMESTAMPTZ DEFAULT now(),
    updated_at TIMESTAMPTZ DEFAULT now(),
    UNIQUE(order_id, customer_id)
);

CREATE INDEX IF NOT EXISTS idx_driver_ratings_driver_time
    ON driver_ratings(driver_id, created_at DESC);

CREATE TABLE IF NOT EXISTS driver_rating_tags (
    id BIGSERIAL PRIMARY KEY,
    rating_id VARCHAR(40) NOT NULL,
    tag_name VARCHAR(50) NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_driver_rating_tags_rating
    ON driver_rating_tags(rating_id);

CREATE TABLE IF NOT EXISTS unload_address_records (
    id BIGSERIAL PRIMARY KEY,
    record_id VARCHAR(40) UNIQUE NOT NULL,
    order_id VARCHAR(64) NOT NULL,
    record_type VARCHAR(30) NOT NULL,
    address TEXT,
    lng DOUBLE PRECISION,
    lat DOUBLE PRECISION,
    abnormal_type VARCHAR(50),
    description TEXT,
    photos TEXT,
    remark TEXT,
    operator_id VARCHAR(40),
    operator_role VARCHAR(30),
    created_at TIMESTAMPTZ DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_unload_address_order_type_time
    ON unload_address_records(order_id, record_type, created_at DESC);
