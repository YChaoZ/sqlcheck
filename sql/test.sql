-- 测试SQL语法检查
CREATE TABLE IF NOT EXISTS test_table (
    id INT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(50) DEFAULT NULL,
    score FLOAT DEFAULT NULL,
    price DOUBLE DEFAULT NULL,
    desc TEXT DEFAULT 'test',
    content BLOB DEFAULT NULL,
    data JSON DEFAULT NULL,
    email VARCHAR(0) NOT NULL,
    idx INT(0),
    big_field VARCHAR(30000),
    UNIQUE KEY (name),
    INDEX idx_name (name),
    UNIQUE KEY my_unique (email)
);

INSERT INTO test_table (name, score) VALUES ('test', 99.5);

ALTER TABLE test_table ADD COLUMN age INT DEFAULT NULL;

DROP INDEX idx_name;
DROP TABLE my_table;
