-- ----------------------------------------------------------------------------
-- 课程管理模块 · 管理端菜单（详细设计 §2.2；页面：frontend/pc/src/views/course/index.vue）
-- 口径：RBAC 走若依框架 —— 菜单表 sys_menu 驱动前端动态路由（component = views 下的相对路径）
--     本次只挂「目录 + 菜单」，**不写按钮权限串**（2026-09-22 决策，与 §2.1.2「RBAC 未确认」一致）；
--     因此前端按钮也没有 v-hasPermi，后端课程接口也没有 @PreAuthorize。
-- 前置：已在目标库执行过 backend/sql/ry_20260417.sql（sys_menu / sys_role_menu 存在）
-- 幂等：脚本先删除本模块占用的 menu_id（2000、2001），可重复执行
-- 清理：执行 DELETE FROM sys_role_menu WHERE menu_id IN (2000, 2001);
--              DELETE FROM sys_menu WHERE menu_id IN (2000, 2001);
-- ----------------------------------------------------------------------------

DELETE FROM `sys_role_menu` WHERE `menu_id` IN (2000, 2001);
DELETE FROM `sys_menu` WHERE `menu_id` IN (2000, 2001);

-- 一级目录：经营基础（门店 / 课程 / 教练 / 排班 / 预约 后续都挂在这个目录下）
insert into sys_menu values('2000', '经营基础', '0',    '5', 'operation', null,           '', '', 1, 0, 'M', '0', '0', '',          'education', 'admin', sysdate(), '', null, '经营基础目录');
-- 二级菜单：课程管理（路由 /operation/course，组件 src/views/course/index.vue）
insert into sys_menu values('2001', '课程管理', '2000', '1', 'course',    'course/index', '', '', 1, 0, 'C', '0', '0', '',          'list',      'admin', sysdate(), '', null, '课程管理菜单');

-- 角色绑定：role_id = 1 是若依内置管理员（它的菜单本来就不受 sys_role_menu 限制，
-- 这里补上是为了让角色-菜单关系在「角色管理」页面里可见、可维护）
insert into sys_role_menu values ('1', '2000');
insert into sys_role_menu values ('1', '2001');

-- ----------------------------------------------------------------------------
-- 后续启用按钮级权限时，把下面 5 行取消注释一起执行，并同步：
--   ① 前端 src/views/course/index.vue 的按钮补 v-hasPermi（文件里已留注释标注位置）
--   ② 后端 CourseController 的 5 个方法补 @PreAuthorize("@ss.hasPermi('course:course:xxx')")
-- 注意：这一步等于把 §2.1.2 的「RBAC 未确认（E09）」定下来，请先与产品/团队确认。
-- ----------------------------------------------------------------------------
-- insert into sys_menu values('2002', '课程查询', '2001', '1', '#', '', '', '', 1, 0, 'F', '0', '0', 'course:course:list',   '#', 'admin', sysdate(), '', null, '');
-- insert into sys_menu values('2003', '课程详情', '2001', '2', '#', '', '', '', 1, 0, 'F', '0', '0', 'course:course:query',  '#', 'admin', sysdate(), '', null, '');
-- insert into sys_menu values('2004', '课程新增', '2001', '3', '#', '', '', '', 1, 0, 'F', '0', '0', 'course:course:add',    '#', 'admin', sysdate(), '', null, '');
-- insert into sys_menu values('2005', '课程修改', '2001', '4', '#', '', '', '', 1, 0, 'F', '0', '0', 'course:course:edit',   '#', 'admin', sysdate(), '', null, '');
-- insert into sys_menu values('2006', '课程启停', '2001', '5', '#', '', '', '', 1, 0, 'F', '0', '0', 'course:course:status', '#', 'admin', sysdate(), '', null, '');
-- insert into sys_role_menu values ('1', '2002'), ('1', '2003'), ('1', '2004'), ('1', '2005'), ('1', '2006');
