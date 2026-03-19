-- Apollo自动化配置脚本
INSERT KEY:app.name VALUE:test-app COMMENT:应用名称;
INSERT KEY:app.version VALUE:1.0.0 COMMENT:应用版本;
INSERT KEY:db.url VALUE:jdbc:mysql://localhost:3306/test COMMENT:数据库连接;
INSERT KEY:db.maxPoolSize VALUE:10 COMMENT:最大连接池;
UPDATE KEY:app.version VALUE:1.0.1 COMMENT:更新版本;
DELETE KEY:old.key VALUE:deleteme;
