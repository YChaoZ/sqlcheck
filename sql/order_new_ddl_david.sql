-- 正常DDL示例 - 全部通过检查
CREATE TABLE IF NOT EXISTS order_info (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_no VARCHAR(64) NOT NULL,
    user_id BIGINT NOT NULL,
    total_amount DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    status INT NOT NULL DEFAULT 1 COMMENT '1:待支付 2:已支付 3:已完成 4:已取消',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_order_no (order_no),
    KEY idx_user_id (user_id),
    KEY idx_created_at (created_at)
) ENGINE=InnoDB CHARSET=utf8mb4 COMMENT='订单信息表';

ALTER TABLE order_info ADD COLUMN remark VARCHAR(256) AFTER status;

DROP INDEX idx_user_id ON order_info;