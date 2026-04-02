-- 通过版DDL示例，用于验证按提交人合并
ALTER TABLE biz_table ADD COLUMN email VARCHAR(128) AFTER name;