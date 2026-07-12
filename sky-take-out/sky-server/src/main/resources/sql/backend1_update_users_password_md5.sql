-- 如果你已经执行过旧版初始化脚本，运行这个脚本把演示账号密码改为 MD5。
-- 123456 -> e10adc3949ba59abbe56e057f20f883e

UPDATE users
SET password_hash = 'e10adc3949ba59abbe56e057f20f883e',
    updated_at = now()
WHERE username IN ('shipper', 'dispatcher', 'warehouse', 'admin', 'driver');
