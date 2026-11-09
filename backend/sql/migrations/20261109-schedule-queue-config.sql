-- 排课新增排队上限与停止排队时间：已导入 yoga.sql 的库执行本脚本补列，重复执行不报错。
-- 新建库直接导入 yoga.sql 即可，无需执行本脚本。

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 't_schedule' AND COLUMN_NAME = 'queue_limit') = 0,
  'ALTER TABLE `t_schedule` ADD COLUMN `queue_limit` int NOT NULL DEFAULT 3 COMMENT ''排队上限（0 表示不开放排队）'' AFTER `min_persons`',
  'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 't_schedule' AND COLUMN_NAME = 'queue_stop_minutes') = 0,
  'ALTER TABLE `t_schedule` ADD COLUMN `queue_stop_minutes` int NOT NULL DEFAULT 110 COMMENT ''停止排队时间：开课前多少分钟起不能再排队，也不再补位'' AFTER `queue_limit`',
  'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
