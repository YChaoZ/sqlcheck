-- Apollo SQL格式配置 - 正常示例
INSERT KEY:app.name VALUE:test-app COMMENT:应用名称;
INSERT KEY:app.version VALUE:1.0.0 COMMENT:应用版本;
INSERT KEY:db.url VALUE:jdbc:mysql://localhost:3306/test COMMENT:数据库连接;
INSERT KEY:db.maxPoolSize VALUE:10 COMMENT:最大连接池;
INSERT KEY:db.timeout VALUE:3000 COMMENT:超时时间毫秒;
UPDATE KEY:app.version VALUE:1.0.1 COMMENT:更新版本;
UPDATE KEY:db.maxPoolSize VALUE:20 COMMENT:增加连接池;
DELETE KEY:old.key;
DELETE KEY:unused.config;