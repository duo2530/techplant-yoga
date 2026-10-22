-- ============================================================================
-- 瑜伽普拉提 · 已有库增量升级脚本：教练金牌等级（v2.6）
--
-- 用法：在已有业务库上执行，补充教练等级列并清理已废止的等级按钮权限；
--       新建库直接导入最新 yoga.sql，无需执行本脚本。
--
-- 口径：
--   1. 只增加 t_coach.level 列（1 普通教练／2 金牌教练，默认 1），不重建表、不改旧数据；
--      既有教练在加列后一律为普通等级，不回填任何样例金牌。
--   2. 教练等级维护只要求管理员登录，清理已废止的等级按钮及其角色绑定。
--   3. 可重复执行：列已存在时跳过加列，不改动其它菜单与角色授权。
-- ============================================================================

SET NAMES utf8mb4;

-- ----------------------------------------------------------------------------
-- 1. 教练等级列
-- ----------------------------------------------------------------------------
SET @level_column_exists = (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_coach' AND COLUMN_NAME = 'level'
);
SET @add_level_column = IF(@level_column_exists = 0,
  'ALTER TABLE `t_coach` ADD COLUMN `level` tinyint NOT NULL DEFAULT 1 COMMENT ''教练等级：1普通 2金牌'' AFTER `gallery`',
  'SELECT 1');
PREPARE add_level_column_statement FROM @add_level_column;
EXECUTE add_level_column_statement;
DEALLOCATE PREPARE add_level_column_statement;

-- ----------------------------------------------------------------------------
-- 2. 清理已废止的教练等级按钮权限
-- ----------------------------------------------------------------------------
DELETE rm FROM `sys_role_menu` rm
INNER JOIN `sys_menu` m ON m.`menu_id` = rm.`menu_id`
WHERE m.`menu_id` = 2010 AND m.`perms` = 'yoga:coach:level';
DELETE FROM `sys_menu` WHERE `menu_id` = 2010 AND `perms` = 'yoga:coach:level';

-- ----------------------------------------------------------------------------
-- 3. 回滚说明
--   · 应用回滚：保留 level 列与已标记数据即可回退旧后端；旧代码的资料更新显式列不含 level，
--     不会覆盖等级，列表/详情/公开接口都能继续运行。
--   · 本脚本不提供自动 DROP 列：撤销列会丢失已有的金牌标记，需要另行备份并执行维护操作。
--   · 等级操作不依赖按钮权限；回滚应用不需要恢复等级按钮与角色绑定。
-- ----------------------------------------------------------------------------
