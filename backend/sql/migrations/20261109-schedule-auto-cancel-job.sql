-- 新增“人数不足自动取消排课”定时任务：已导入 yoga.sql 的库执行本脚本补任务，重复执行不报错，已有同 ID 任务时保持不变。
-- 新建库直接导入 yoga.sql 即可，无需执行本脚本。执行后重启后端，定时任务在启动时从 sys_job 加载。

INSERT INTO `sys_job` (`job_id`, `job_name`, `job_group`, `invoke_target`, `cron_expression`, `misfire_policy`,
  `concurrent`, `status`, `create_by`, `create_time`, `remark`)
SELECT 100, '人数不足自动取消排课', 'DEFAULT', 'yogaScheduleTask.cancelUnderstaffed', '0 * * * * ?', '3', '1', '0',
  'admin', SYSDATE(), '每分钟扫描开课前 120 分钟内、已预约人数不足最低开课人数的已上架排课并自动取消'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `sys_job` WHERE `job_id` = 100);
