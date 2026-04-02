-- DML测试样本 - 仅包含更新表操作语句
-- 正常INSERT
INSERT INTO t1 (name) VALUES ('alice');

-- 正常INSERT多行
INSERT INTO t1 (name) VALUES ('bob'), ('charlie'), ('david');

-- INSERT IGNORE - 无WARNING（有DELETE语义）
INSERT IGNORE INTO t1 (name) VALUES ('duplicate');

-- REPLACE - 无WARNING（有DELETE语义）
REPLACE INTO t1 (name) VALUES ('replace_test');

-- INSERT ON DUPLICATE KEY UPDATE - 无WARNING
INSERT INTO t1 (name) VALUES ('new1')
ON DUPLICATE KEY UPDATE name = 'updated1';

-- INSERT无DELETE配合 - WARNING
INSERT INTO t1 (name) VALUES ('no_delete');

-- UPDATE有WHERE - 正常
UPDATE t1 SET name = 'updated' WHERE id = 1;

-- UPDATE无WHERE - ERROR
UPDATE t1 SET name = 'no_where';

-- UPDATE WHERE 1=1 - WARNING
UPDATE t1 SET name = 'where_one_equals_one' WHERE 1=1;

-- UPDATE LIMIT有WHERE - 正常
UPDATE t1 SET name = 'limit_update' WHERE id > 0 LIMIT 10;

-- UPDATE LIMIT无WHERE - WARNING
UPDATE t1 SET name = 'limit_no_where' LIMIT 5;

-- DELETE有WHERE - 正常
DELETE FROM t1 WHERE id = 2;

-- DELETE无WHERE - WARNING
DELETE FROM t1;

-- DELETE WHERE 1=1 - WARNING
DELETE FROM t1 WHERE 1=1;

-- DELETE LIMIT有WHERE - 正常
DELETE FROM t1 WHERE id > 100 LIMIT 20;

-- DELETE LIMIT无WHERE - WARNING
DELETE FROM t1 LIMIT 10;

-- 最佳实践: SELECT * - WARNING
SELECT * FROM t1;

-- 最佳实践: SELECT具体字段 - 正常
SELECT id, name FROM t1;

-- 最佳实践: LIMIT offset,count - WARNING
SELECT id, name FROM t1 LIMIT 10, 20;

-- 最佳实践: LIMIT count - 正常
SELECT id, name FROM t1 LIMIT 50;

-- 基础: 中文标点 - ERROR
SELECT id FROM t1 WHERE name = 测试;

-- 基础: 缺少分号 - ERROR
INSERT INTO t1 (name) VALUES ('no_semi')

-- 字符串含中文标点 - 正常，不报错
INSERT INTO t1 (name) VALUES ('中文测试，标点');
INSERT INTO t1 (name) VALUES ('你好，世界');
INSERT INTO t1 (name) VALUES ('it''s 中文，ok');