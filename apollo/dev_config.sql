-- Apollo配置错误示例
-- 重复KEY - ERROR
INSERT KEY:db.url VALUE:jdbc:mysql://localhost:3306/test COMMENT:数据库连接;
INSERT KEY:db.url VALUE:jdbc:mysql://localhost:3306/prod COMMENT:生产库;

-- 缺少KEY - ERROR
INSERT KEY: VALUE:mysql COMMENT:缺少KEY;

-- 缺少VALUE - ERROR
INSERT KEY:db.user COMMENT:缺少VALUE;

-- 中文冒号 - ERROR
INSERT KEY:db.host VALUE:mysql（中文冒号）;

-- 缺少分号 - ERROR
INSERT KEY:app.name VALUE:test

-- KEY格式不规范 - WARNING
INSERT KEY:ab-cd VALUE:value COMMENT:包含短横线;
INSERT KEY:123 KEY VALUE:value COMMENT:数字开头;

-- 正常语句
INSERT KEY:feature.enabled VALUE:true COMMENT:功能开关;
INSERT KEY:feature.timeout VALUE:5000 COMMENT:超时配置;