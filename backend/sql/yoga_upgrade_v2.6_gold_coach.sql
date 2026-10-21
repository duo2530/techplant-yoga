-- ============================================================================
-- 瑜伽普拉提 · 已有库增量升级脚本：教练金牌等级（v2.6）
--
-- 用法：在已导入旧版 yoga.sql（v2.5 及更早，t_coach 没有 level 列）的库上执行；
--       新建库直接导入最新 yoga.sql，无需执行本脚本。
--
-- 口径：
--   1. 只增加 t_coach.level 列（1 普通教练／2 金牌教练，默认 1），不重建表、不改旧数据；
--      既有教练在加列后一律为普通等级，不回填任何样例金牌。
--   2. 增补教练管理（2003）下的按钮权限 yoga:coach:level 与内置管理员角色绑定。
--   3. 可重复执行：列已存在时跳过加列；菜单行缺失时补建、已存在时保持原内容；
--      只补内置管理员角色的 2010 绑定，其它角色已授予的 2010 授权保留，不会被清空。
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
-- 2. 按钮权限 yoga:coach:level（教练管理 2003 下）
--    菜单行用 INSERT IGNORE：已存在时保持原内容，不删除重建；
--    角色绑定只补内置管理员（role_id = 1），其它角色已授予的 2010 绑定保留。
-- ----------------------------------------------------------------------------
INSERT IGNORE INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`,
  `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`,
  `update_by`, `update_time`, `remark`)
VALUES ('2010', '金牌教练设置', '2003', '1', '', '', '', '', 1, 0, 'F', '0', '0', 'yoga:coach:level', '#',
  'admin', SYSDATE(), '', NULL, '设置或取消教练金牌的按钮权限');

INSERT IGNORE INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES ('1', '2010');

-- ----------------------------------------------------------------------------
-- 3. 回滚说明
--   · 应用回滚：保留 level 列与已标记数据即可回退旧后端；旧代码的资料更新显式列不含 level，
--     不会覆盖等级，列表/详情/公开接口都能继续运行。
--   · 本脚本不提供自动 DROP 列：撤销列会丢失已有的金牌标记，需要另行备份并执行维护操作。
--   · 菜单与角色绑定：回退旧后端不影响这些行；若需彻底撤销，删除 menu_id = 2010 的
--     sys_role_menu 与 sys_menu 行即可，不影响其它业务菜单与其它角色授权。
-- ----------------------------------------------------------------------------
