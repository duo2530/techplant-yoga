-- 预约新增按会员与排课查询的索引：已导入 yoga.sql 的库执行本脚本补索引，重复执行不报错。
-- 新建库直接导入 yoga.sql 即可，无需执行本脚本。

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 't_reservation' AND INDEX_NAME = 'idx_reservation_member_schedule') = 0,
  'ALTER TABLE `t_reservation` ADD INDEX `idx_reservation_member_schedule` (`member_id`, `schedule_id`), ALGORITHM=INPLACE, LOCK=NONE',
  'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
