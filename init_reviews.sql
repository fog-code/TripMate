-- ============================================================
-- 景点评论表
-- ============================================================
CREATE TABLE IF NOT EXISTS reviews (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT      NOT NULL COMMENT '评论用户ID',
    attraction_id BIGINT    NOT NULL COMMENT '景点ID',
    content     TEXT        NOT NULL COMMENT '评论内容',
    rating      TINYINT     NOT NULL DEFAULT 5 COMMENT '评分 1-5',
    created_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_attraction (attraction_id, created_at),
    INDEX idx_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='景点评论表';
