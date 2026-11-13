-- 会员卡新增按会员查询的索引：已导入 yoga.sql 的库执行本脚本补索引，重复执行不报错。
-- 新建库直接导入 yoga.sql 即可，无需执行本脚本。

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 't_member_card' AND INDEX_NAME = 'idx_member_card_member_type') = 0,
  'ALTER TABLE `t_member_card` ADD INDEX `idx_member_card_member_type` (`member_id`, `card_type`), ALGORITHM=INPLACE, LOCK=NONE',
  'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
