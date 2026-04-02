-- DDL测试样本 - 仅包含建表相关语句
-- 语法错误: FLOAT DEFAULT NULL - ERROR
CREATE TABLE t_float (
    id BIGINT,
    price FLOAT DEFAULT NULL
);

-- 语法错误: VARCHAR(0) - ERROR
CREATE TABLE t_varchar0 (
    id BIGINT,
    name VARCHAR(0)
);

-- 语法错误: TEXT DEFAULT - ERROR
CREATE TABLE t_text (
    id BIGINT,
    content TEXT DEFAULT 'default'
);

-- 语法错误: INDEX空括号 - ERROR
CREATE TABLE t_idx_empty (
    id BIGINT,
    name VARCHAR(50),
    INDEX ()
);

-- 语法错误: UNIQUE KEY无名称 - ERROR
CREATE TABLE t_unique (
    id BIGINT,
    name VARCHAR(50),
    UNIQUE (name)
);

-- 语法错误: COMMENT未闭合 - ERROR
CREATE TABLE t_bad_comment (
    id BIGINT COMMENT '未闭合
);

-- 语法错误: AUTO_INCREMENT=0 - ERROR
CREATE TABLE t_auto_zero (
    id BIGINT AUTO_INCREMENT=0 PRIMARY KEY
);

-- 语法错误: VARCHAR长度>21845 - ERROR
CREATE TABLE t_long_varchar (
    id BIGINT,
    content VARCHAR(30000)
);

-- 语法错误: ENUM未闭合 - ERROR
CREATE TABLE t_enum_bad (
    id BIGINT,
    status ENUM('active'
);

-- 语法错误: DATE DEFAULT CURRENT_TIMESTAMP - ERROR
CREATE TABLE t_date_default (
    id BIGINT,
    created_date DATE DEFAULT CURRENT_TIMESTAMP
);

-- 语法错误: TEXT做KEY - ERROR
CREATE TABLE t_text_key (
    id BIGINT,
    content TEXT,
    KEY idx_content (content)
);

-- 语法错误: 列名超64字符 - ERROR
CREATE TABLE t_long_col (
    id BIGINT,
    this_is_a_very_long_column_name_that_is_definitely_longer_than_sixty_four_characters BIGINT
);

-- 语法错误: 列名数字开头 - ERROR
CREATE TABLE t_digit_col (
    id BIGINT,
    123column BIGINT
);

-- 基础: 缺少结束分号 - ERROR
CREATE TABLE t_no_semi (
    id BIGINT PRIMARY KEY
)

-- 基础: 中文标点 - ERROR
CREATE TABLE t_chinese (
    id BIGINT
);

-- 缺少IF NOT EXISTS - WARNING
CREATE TABLE t_no_if_not_exists (
    id BIGINT PRIMARY KEY
);

-- 缺少IF EXISTS - WARNING
DROP TABLE t_old;

-- 缺少IF EXISTS - WARNING
DROP INDEX idx_old;

-- 最佳实践: INT AUTO_INCREMENT - WARNING
CREATE TABLE t_int_auto (
    id INT AUTO_INCREMENT PRIMARY KEY
);

-- 最佳实践: 未指定CHARSET - INFO
CREATE TABLE t_no_charset (
    id BIGINT PRIMARY KEY
);

-- 最佳实践: 未指定ENGINE - INFO
CREATE TABLE t_no_engine (
    id BIGINT PRIMARY KEY
);

-- 最佳实践: MYISAM已废弃 - WARNING
CREATE TABLE t_myisam (
    id BIGINT PRIMARY KEY
) ENGINE=MyISAM;

-- 最佳实践: MEMORY不持久化 - WARNING
CREATE TABLE t_memory (
    id BIGINT PRIMARY KEY
) ENGINE=MEMORY;

-- 最佳实践: VARCHAR直接做KEY无前缀 - INFO
CREATE TABLE t_varchar_key (
    id BIGINT,
    name VARCHAR(100),
    KEY idx_name (name)
);

-- 最佳实践: 联合索引超过6列 - WARNING
CREATE TABLE t_multi_idx (
    id BIGINT,
    c1 VARCHAR(10),
    c2 VARCHAR(10),
    c3 VARCHAR(10),
    c4 VARCHAR(10),
    c5 VARCHAR(10),
    c6 VARCHAR(10),
    c7 VARCHAR(10),
    KEY idx_multi (c1, c2, c3, c4, c5, c6, c7)
);

-- 最佳实践: 保留字作为列名 - WARNING
CREATE TABLE t_reserved (
    id BIGINT,
    table_name VARCHAR(50),
    index_col VARCHAR(50)
);

-- 最佳实践: utf8已废弃 - WARNING
CREATE TABLE t_utf8 (
    id BIGINT PRIMARY KEY
) CHARSET=utf8;

-- 最佳实践: ZEROFILL已废弃 - WARNING
CREATE TABLE t_zerofill (
    id BIGINT ZEROFILL
);

-- 最佳实践: CREATE TABLE AS SELECT - WARNING
CREATE TABLE t_ctas AS SELECT * FROM another_table;

-- 危险: TRUNCATE TABLE - WARNING
TRUNCATE TABLE t_truncate;

-- 危险: DROP PRIMARY KEY - WARNING
ALTER TABLE t_primary DROP PRIMARY KEY;

-- 危险: DROP UNIQUE KEY - WARNING
ALTER TABLE t_unique DROP KEY uq_name;

-- 危险: ALTER TABLE缺少IF EXISTS - WARNING
ALTER TABLE t_not_exists ADD COLUMN col1 INT;