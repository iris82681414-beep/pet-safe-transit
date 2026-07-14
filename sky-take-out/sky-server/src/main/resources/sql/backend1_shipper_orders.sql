-- 货主订单扩展：可重复执行，不删除现有数据。

ALTER TABLE cargo ADD COLUMN IF NOT EXISTS owner_id VARCHAR(32) REFERENCES users(id);
ALTER TABLE cargo ADD COLUMN IF NOT EXISTS pet_name VARCHAR(64);
ALTER TABLE cargo ADD COLUMN IF NOT EXISTS pet_breed VARCHAR(64);
ALTER TABLE cargo ADD COLUMN IF NOT EXISTS pet_age VARCHAR(32);
ALTER TABLE cargo ADD COLUMN IF NOT EXISTS pet_gender VARCHAR(16);
ALTER TABLE cargo ADD COLUMN IF NOT EXISTS contact_name VARCHAR(64);
ALTER TABLE cargo ADD COLUMN IF NOT EXISTS contact_phone VARCHAR(32);
ALTER TABLE cargo ADD COLUMN IF NOT EXISTS receiver_name VARCHAR(64);
ALTER TABLE cargo ADD COLUMN IF NOT EXISTS receiver_phone VARCHAR(32);
ALTER TABLE cargo ADD COLUMN IF NOT EXISTS request_note TEXT;

CREATE INDEX IF NOT EXISTS idx_cargo_owner_time ON cargo(owner_id, created_at DESC);

-- 旧演示数据归到默认货主，线上新增订单会从 JWT 写入真实 owner_id。
UPDATE cargo
SET owner_id = 'USR-001'
WHERE owner_id IS NULL
  AND EXISTS (SELECT 1 FROM users WHERE id = 'USR-001');

UPDATE cargo
SET cargo_type = CASE
        WHEN cargo_id = 'SH-HZ-20260629-0291' THEN '犬'
        WHEN cargo_type IS NULL OR cargo_type NOT IN ('犬', '猫', '其他宠物') THEN '宠物'
        ELSE cargo_type
    END,
    pet_name = CASE
        WHEN cargo_id = 'SH-HZ-20260629-0291' THEN '布丁'
        WHEN pet_name IS NULL OR pet_name LIKE '%?%' THEN '萌宠-' || RIGHT(cargo_id, 4)
        ELSE pet_name
    END,
    pet_breed = CASE WHEN pet_breed IS NULL OR pet_breed LIKE '%?%' THEN '待补充' ELSE pet_breed END,
    contact_name = CASE WHEN contact_name IS NULL OR contact_name LIKE '%?%' THEN '李货主' ELSE contact_name END,
    contact_phone = COALESCE(contact_phone, '13800000001'),
    receiver_name = CASE WHEN receiver_name IS NULL OR receiver_name LIKE '%?%' THEN '李货主' ELSE receiver_name END,
    receiver_phone = COALESCE(receiver_phone, '13800000001')
WHERE owner_id = 'USR-001';

CREATE TABLE IF NOT EXISTS cargo_environment_readings (
    id BIGSERIAL PRIMARY KEY,
    cargo_id VARCHAR(64) NOT NULL REFERENCES cargo(cargo_id) ON DELETE CASCADE,
    temperature NUMERIC(5,2),
    humidity NUMERIC(5,2),
    air_quality VARCHAR(32),
    vibration VARCHAR(32),
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_cargo_environment_latest
    ON cargo_environment_readings(cargo_id, recorded_at DESC);

UPDATE cargo_environment_readings
SET air_quality = '良好', vibration = '轻微'
WHERE cargo_id = 'SH-HZ-20260629-0291'
  AND (air_quality IS NULL OR vibration IS NULL OR air_quality LIKE '%?%' OR vibration LIKE '%?%');

INSERT INTO cargo_environment_readings(cargo_id, temperature, humidity, air_quality, vibration, recorded_at)
SELECT cargo_id, 24.60, 58.00, '良好', '轻微', now() - interval '2 minutes'
FROM cargo
WHERE cargo_id = 'SH-HZ-20260629-0291'
  AND NOT EXISTS (
      SELECT 1 FROM cargo_environment_readings r
      WHERE r.cargo_id = cargo.cargo_id
  );
