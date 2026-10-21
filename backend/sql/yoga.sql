-- ============================================================================
-- 瑜伽普拉提 · 业务库脚本
-- 版本：v2.6（金牌教练等级）        日期：2026-10-21
--
-- 用法：在已经导入若依基础库（ry_20260417.sql + quartz.sql）的库上执行本脚本。
--       本脚本只负责「业务表 + 业务菜单 + 业务字典」，不涉及 sys_* 的结构。
--
-- 依据：docs/详细设计/ 下的详细设计（总览 / 门店 / 教室 / 教练 / 课程 / 排课 / 用户端接口 / 会员管理）
--
-- v2.1 新增（二期「会员登录与排课浏览」的第一块）：
--   · §3.6 t_member：会员与登录信息表（唯一依据是会员管理详细设计 §1.2.1／§1.3.1）
--   · 1 条**已注册**的种子会员，密码 yoga1234（BCrypt），小程序端可以直接登录
--     手机号 13800138000 → 显示名「忻会员8000」（= 忻会员 + 手机号后 4 位，BR-会员-010）
--
-- v2.2 新增：菜单 2006「会员管理」，指向 frontend/pc/src/views/member/index.vue，
--   对应管理端 /admin/members 的建档／编辑／删除；表结构不变，仍复用 t_member。
--
-- v2.3 新增：字典 member_level「会员等级」（码值 0~4），前端下拉与接口 levelText 翻译的唯一来源。
--
-- v2.4 新增：
--   · t_store 增加 store_no（门店编号，服务端分配、创建后不可修改），并新增编号计数器 t_store_no_counter
--   · t_member_card：会员卡表（唯一依据是会员卡管理详细设计 §1.2.1／§1.3.1）
--   · 菜单 2007「会员卡管理」，指向 frontend/pc/src/views/membercard/index.vue
--
-- v2.5 新增：
--   · t_reservation：课程预约单（唯一依据是预约管理详细设计 §1.2.1／§1.3.1）
--   · t_member_card_transaction：会员卡次数流水（同上）
--   · t_schedule.booked_persons 由预约创建与取消事务维护，不再固定 0
--   · 菜单 2008「预约管理」，指向 frontend/pc/src/views/reservation/index.vue
--
-- v2.6 新增：
--   · t_coach 增加 level（1 普通教练／2 金牌教练，默认 1），供首页金牌教练列表使用
--   · 菜单 2010：教练管理（2003）下的按钮权限 yoga:coach:level
--   · 已有库升级用独立增量脚本 yoga_upgrade_v2.6_gold_coach.sql，不改动本文件的建表语句
--   · 本脚本是「全新建库」口径：会重建 2000~2010 业务菜单与内置管理员绑定；
--     已有库请执行增量脚本，避免覆盖管理员给其它角色授予的业务菜单权限
--
-- 六条硬口径（一期既有口径，v2.1 不变）：
--   1. 业务表一律【物理删除】—— 不建 `deleted` 列、不用 @TableLogic（t_member 同样如此）
--   2. 门店「所在区域」存【行政区划 code】，区名由字典 `store_region` 翻译
--   3. 排课表保存【课种快照 course_type】与【上课日期 schedule_date】两个派生列
--   4. 排课状态：1 待上架 / 2 已上架 / 3 已取消；「已结束」按 end_time 推导，不落库
--   5. 示例排课用 CURDATE() 相对日期生成，保证任何时候导入都能演示「四种状态」
--   6. 预约用 t_reservation：本脚本不含旧表 t_booking
--
-- 业务菜单放在独立的一级目录「经营基础」下，与若依自带的「系统管理」目录互不干扰。
-- ============================================================================

SET NAMES utf8mb4;

-- ============================================================================
-- 1. 菜单：一级目录「经营基础」+ 八个业务菜单
--    （只清理 2000~2010，不动若依自带的「系统管理」那套菜单）
--    2001~2008 已随本脚本交付；2009=账号管理 仍未交付：
--    未交付菜单一并纳入清理范围，菜单行的 INSERT 由各自模块的 SQL 交付时补。
--    2010=教练金牌按钮权限，挂在教练管理（2003）下，随本脚本交付。
-- ============================================================================

DELETE FROM `sys_role_menu` WHERE `menu_id` IN (2000, 2001, 2002, 2003, 2004, 2005, 2006, 2007, 2008, 2009, 2010);
DELETE FROM `sys_menu`      WHERE `menu_id` IN (2000, 2001, 2002, 2003, 2004, 2005, 2006, 2007, 2008, 2009, 2010);

-- 一级目录：经营基础
INSERT INTO `sys_menu` VALUES ('2000', '经营基础', '0',    '5', 'operation',  NULL,            '', '', 1, 0, 'M', '0', '0', '', 'education', 'admin', SYSDATE(), '', NULL, '经营基础目录');

-- 二级菜单（component 对应 frontend/pc/src/views/<name>/index.vue）
INSERT INTO `sys_menu` VALUES ('2001', '门店管理', '2000', '1', 'store',     'store/index',     '', '', 1, 0, 'C', '0', '0', '', 'shopping',  'admin', SYSDATE(), '', NULL, '门店管理菜单');
INSERT INTO `sys_menu` VALUES ('2002', '教室管理', '2000', '2', 'classroom', 'classroom/index', '', '', 1, 0, 'C', '0', '0', '', 'tree',      'admin', SYSDATE(), '', NULL, '教室管理菜单');
INSERT INTO `sys_menu` VALUES ('2003', '教练管理', '2000', '3', 'coach',     'coach/index',     '', '', 1, 0, 'C', '0', '0', '', 'user',      'admin', SYSDATE(), '', NULL, '教练管理菜单');
INSERT INTO `sys_menu` VALUES ('2004', '课程管理', '2000', '4', 'course',    'course/index',    '', '', 1, 0, 'C', '0', '0', '', 'education', 'admin', SYSDATE(), '', NULL, '课程管理菜单');
INSERT INTO `sys_menu` VALUES ('2005', '排课管理', '2000', '5', 'schedule',  'schedule/index',  '', '', 1, 0, 'C', '0', '0', '', 'date',      'admin', SYSDATE(), '', NULL, '排课管理菜单');
INSERT INTO `sys_menu` VALUES ('2006', '会员管理', '2000', '6', 'member',    'member/index',    '', '', 1, 0, 'C', '0', '0', '', 'peoples',   'admin', SYSDATE(), '', NULL, '会员管理菜单');
INSERT INTO `sys_menu` VALUES ('2007', '会员卡管理', '2000', '7', 'membercard', 'membercard/index', '', '', 1, 0, 'C', '0', '0', '', 'money', 'admin', SYSDATE(), '', NULL, '会员卡管理菜单');
INSERT INTO `sys_menu` VALUES ('2008', '预约管理', '2000', '8', 'reservation', 'reservation/index', '', '', 1, 0, 'C', '0', '0', '', 'date', 'admin', SYSDATE(), '', NULL, '预约管理菜单');

-- 按钮权限：设置／取消金牌教练（教练管理 2003 下）
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`,
  `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`,
  `update_time`, `remark`)
VALUES ('2010', '金牌教练设置', '2003', '1', '', '', '', '', 1, 0, 'F', '0', '0', 'yoga:coach:level', '#',
  'admin', SYSDATE(), '', NULL, '设置或取消教练金牌的按钮权限');

-- 绑定若依内置管理员角色（role_id = 1）
INSERT INTO `sys_role_menu` VALUES ('1', '2000');
INSERT INTO `sys_role_menu` VALUES ('1', '2001');
INSERT INTO `sys_role_menu` VALUES ('1', '2002');
INSERT INTO `sys_role_menu` VALUES ('1', '2003');
INSERT INTO `sys_role_menu` VALUES ('1', '2004');
INSERT INTO `sys_role_menu` VALUES ('1', '2005');
INSERT INTO `sys_role_menu` VALUES ('1', '2006');
INSERT INTO `sys_role_menu` VALUES ('1', '2007');
INSERT INTO `sys_role_menu` VALUES ('1', '2008');
INSERT INTO `sys_role_menu` VALUES ('1', '2010');

-- ============================================================================
-- 2. 字典
--    前端下拉与后端「码 → 名」翻译都以这里为唯一来源。
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 2.1 门店「所在区域」（上海市 16 个市辖区的行政区划代码）
--     ⚠️ 代码里的 16 条属于暂定，上线前请逐条核对。
-- ---------------------------------------------------------------------------

DELETE FROM `sys_dict_data` WHERE `dict_type` = 'store_region';
DELETE FROM `sys_dict_type` WHERE `dict_type` = 'store_region';

INSERT INTO `sys_dict_type` (`dict_name`, `dict_type`, `status`, `create_by`, `create_time`, `remark`)
VALUES ('门店所在区域', 'store_region', '0', 'admin', SYSDATE(), '上海市市辖区行政区划代码');

INSERT INTO `sys_dict_data`
  (`dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `status`, `create_by`, `create_time`, `remark`)
VALUES
  ( 1, '黄浦区',   '310101', 'store_region', '', 'default', 'N', '0', 'admin', SYSDATE(), NULL),
  ( 2, '徐汇区',   '310104', 'store_region', '', 'default', 'N', '0', 'admin', SYSDATE(), NULL),
  ( 3, '长宁区',   '310105', 'store_region', '', 'default', 'N', '0', 'admin', SYSDATE(), NULL),
  ( 4, '静安区',   '310106', 'store_region', '', 'default', 'N', '0', 'admin', SYSDATE(), NULL),
  ( 5, '普陀区',   '310107', 'store_region', '', 'default', 'N', '0', 'admin', SYSDATE(), NULL),
  ( 6, '虹口区',   '310109', 'store_region', '', 'default', 'N', '0', 'admin', SYSDATE(), NULL),
  ( 7, '杨浦区',   '310110', 'store_region', '', 'default', 'N', '0', 'admin', SYSDATE(), NULL),
  ( 8, '闵行区',   '310112', 'store_region', '', 'default', 'N', '0', 'admin', SYSDATE(), NULL),
  ( 9, '宝山区',   '310113', 'store_region', '', 'default', 'N', '0', 'admin', SYSDATE(), NULL),
  (10, '嘉定区',   '310114', 'store_region', '', 'default', 'N', '0', 'admin', SYSDATE(), NULL),
  (11, '浦东新区', '310115', 'store_region', '', 'default', 'N', '0', 'admin', SYSDATE(), NULL),
  (12, '金山区',   '310116', 'store_region', '', 'default', 'N', '0', 'admin', SYSDATE(), NULL),
  (13, '松江区',   '310117', 'store_region', '', 'default', 'N', '0', 'admin', SYSDATE(), NULL),
  (14, '青浦区',   '310118', 'store_region', '', 'default', 'N', '0', 'admin', SYSDATE(), NULL),
  (15, '奉贤区',   '310120', 'store_region', '', 'default', 'N', '0', 'admin', SYSDATE(), NULL),
  (16, '崇明区',   '310151', 'store_region', '', 'default', 'N', '0', 'admin', SYSDATE(), NULL);

-- ---------------------------------------------------------------------------
-- 2.2 会员等级（member_level）：码值 0~4
--     管理端会员管理的新增／修改下拉、列表筛选，以及接口 levelText 的翻译都读这里；
--     字典里停用（status = '1'）的等级既不能提交，也不再参与翻译。
--     等级码写入 t_member.level，因此【调整码值等于改数据】，只增不改。
-- ---------------------------------------------------------------------------

DELETE FROM `sys_dict_data` WHERE `dict_type` = 'member_level';
DELETE FROM `sys_dict_type` WHERE `dict_type` = 'member_level';

INSERT INTO `sys_dict_type` (`dict_name`, `dict_type`, `status`, `create_by`, `create_time`, `remark`)
VALUES ('会员等级', 'member_level', '0', 'admin', SYSDATE(), '会员等级：0 V0普通会员 ~ 4 V4黑金会员');

INSERT INTO `sys_dict_data`
  (`dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `status`, `create_by`, `create_time`, `remark`)
VALUES
  (1, 'V0 普通会员', '0', 'member_level', '', 'default', 'N', '0', 'admin', SYSDATE(), NULL),
  (2, 'V1 忻会员',   '1', 'member_level', '', 'default', 'N', '0', 'admin', SYSDATE(), NULL),
  (3, 'V2 银会员',   '2', 'member_level', '', 'default', 'N', '0', 'admin', SYSDATE(), NULL),
  (4, 'V3 金会员',   '3', 'member_level', '', 'default', 'N', '0', 'admin', SYSDATE(), NULL),
  (5, 'V4 黑金会员', '4', 'member_level', '', 'default', 'N', '0', 'admin', SYSDATE(), NULL);

-- ============================================================================
-- 3. 业务表
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 3.1 门店 t_store
--     无状态、无经营类型、无省市区三级 code（只有区级 region_code）、无 deleted
--     store_no 由服务端按创建顺序分配（0001~9999，全平台唯一），创建后不可修改、删除后不复用
--     既有表的 region / province_code / city_code / district_code / business_type /
--     status / deleted 全部不再保留
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS `t_store`;
CREATE TABLE `t_store` (
  `id`             bigint unsigned NOT NULL COMMENT '门店ID',
  `store_no`       char(4)      NOT NULL COMMENT '门店编号（服务端分配，0001~9999，不复用）',
  `name`           varchar(64)  NOT NULL COMMENT '门店名称（全平台唯一）',
  `store_type`     tinyint      NOT NULL COMMENT '门店类型：1主力店 2精品店',
  `region_code`    varchar(6)   NOT NULL COMMENT '所在区域：上海市市辖区行政区划代码（字典 store_region）',
  `phone`          varchar(32)  NOT NULL COMMENT '联系电话',
  `business_hours` varchar(64)  NOT NULL COMMENT '营业时间展示文本',
  `address`        varchar(255) NOT NULL COMMENT '地址（用户端搜索关键字命中名称与地址）',
  `image_url`      varchar(255)          DEFAULT NULL COMMENT '门店图片URL（单图）',
  `create_by`      bigint unsigned       DEFAULT NULL COMMENT '创建人ID',
  `create_time`    datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`      bigint unsigned       DEFAULT NULL COMMENT '更新人ID',
  `update_time`    datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_store_no` (`store_no`),
  UNIQUE KEY `uk_store_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='门店基础信息表';

INSERT INTO `t_store`
  (`id`, `store_no`, `name`, `store_type`, `region_code`, `phone`, `business_hours`, `address`, `image_url`, `create_by`, `create_time`, `update_by`, `update_time`)
VALUES
  (1856739201475235901, '0001', '徐汇店',     1, '310104', '021-12345678', '周一至周日 09:00-22:00', '漕溪北路 88 号',   'https://cdn.example.com/store/xuhui.jpg',  1, '2026-09-20 09:00:00', 1, '2026-09-20 09:00:00'),
  (1856739201475235902, '0002', '静安精品店', 2, '310106', '021-87654321', '周一至周日 10:00-21:00', '愚园路 168 号',    NULL,                                       1, '2026-09-20 09:10:00', 1, '2026-09-20 09:10:00'),
  (1856739201475235903, '0003', '浦东店',     1, '310115', '021-66668888', '周一至周日 08:30-22:00', '世纪大道 1000 号', NULL,                                       1, '2026-09-20 09:20:00', 1, '2026-09-20 09:20:00');

-- ---------------------------------------------------------------------------
-- 3.1.1 门店编号计数器 t_store_no_counter
--     新增门店时在同一个事务里 SELECT ... FOR UPDATE 锁定本行、检查上限后递增；
--     下面的初值 3 对应本脚本 3 条示例门店已占用 0001~0003。
--     已有数据的库若从旧版本升级，初值必须不低于任何历史已分配编号（见门店管理详细设计 §4.3.1）。
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS `t_store_no_counter`;
CREATE TABLE `t_store_no_counter` (
  `id`      tinyint unsigned NOT NULL COMMENT '固定为1',
  `last_no` int unsigned     NOT NULL COMMENT '已分配的最大门店编号',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='门店编号计数器';

INSERT INTO `t_store_no_counter` (`id`, `last_no`) VALUES (1, 3);

-- ---------------------------------------------------------------------------
-- 3.2 教室 t_classroom
--     只有「所属门店 + 名称」；无状态、无容量/设备/图片；同一门店内名称唯一
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS `t_classroom`;
CREATE TABLE `t_classroom` (
  `id`          bigint unsigned NOT NULL COMMENT '教室ID',
  `store_id`    bigint unsigned NOT NULL COMMENT '所属门店ID',
  `name`        varchar(32)  NOT NULL COMMENT '教室名称（同一门店内唯一）',
  `create_by`   bigint unsigned       DEFAULT NULL COMMENT '创建人ID',
  `create_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`   bigint unsigned       DEFAULT NULL COMMENT '更新人ID',
  `update_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_classroom_store_name` (`store_id`, `name`),
  KEY `idx_classroom_store` (`store_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='教室信息表';

INSERT INTO `t_classroom` (`id`, `store_id`, `name`, `create_by`, `create_time`, `update_by`, `update_time`)
VALUES
  (1856739201475236001, 1856739201475235901, '瑜伽团课大教室',   1, '2026-09-20 09:30:00', 1, '2026-09-20 09:30:00'),
  (1856739201475236002, 1856739201475235901, '普拉提器械教室',   1, '2026-09-20 09:31:00', 1, '2026-09-20 09:31:00'),
  (1856739201475236003, 1856739201475235902, '精品小班教室',     1, '2026-09-20 09:32:00', 1, '2026-09-20 09:32:00'),
  (1856739201475236004, 1856739201475235903, '团课大教室',       1, '2026-09-20 09:33:00', 1, '2026-09-20 09:33:00');

-- ---------------------------------------------------------------------------
-- 3.3 教练 t_coach
--     平台级、不关联门店；名称允许重名（不建唯一索引）；无状态、无头衔/排序
--     level：1 普通教练（默认）／2 金牌教练，由管理端独立接口维护
--     相册沿用既有 album_urls 的 json 类型，本版列名统一为 gallery（≤5 张）
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS `t_coach`;
CREATE TABLE `t_coach` (
  `id`          bigint unsigned NOT NULL COMMENT '教练ID',
  `name`        varchar(32)  NOT NULL COMMENT '姓名（允许重名）',
  `avatar_url`  varchar(255)          DEFAULT NULL COMMENT '头像URL',
  `intro`       varchar(512)          DEFAULT NULL COMMENT '简介',
  `phone`       varchar(32)           DEFAULT NULL COMMENT '联系电话（管理端专用）',
  `gallery`     json                  DEFAULT NULL COMMENT '相册：JSON数组，最多5个URL',
  `level`       tinyint      NOT NULL DEFAULT 1 COMMENT '教练等级：1普通 2金牌',
  `create_by`   bigint unsigned       DEFAULT NULL COMMENT '创建人ID',
  `create_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`   bigint unsigned       DEFAULT NULL COMMENT '更新人ID',
  `update_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_coach_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='教练信息表';

-- 示例数据里【故意放两个同名「林教练」】，用于验证「教练名称允许重名」
INSERT INTO `t_coach`
  (`id`, `name`, `avatar_url`, `intro`, `phone`, `gallery`, `create_by`, `create_time`, `update_by`, `update_time`)
VALUES
  (1856739201475237001, '林教练',
   'https://cdn.example.com/coach/avatar-1.jpg', '专注基础体式与呼吸练习，适合零基础学员。', '13800000001',
   '["https://cdn.example.com/coach/album-1.jpg", "https://cdn.example.com/coach/album-2.jpg"]',
   1, '2026-09-28 10:00:00', 1, '2026-09-28 10:00:00'),
  (1856739201475237002, '周教练',
   'https://cdn.example.com/coach/avatar-2.jpg', '擅长普拉提与体态调整训练。', '13800000002',
   '["https://cdn.example.com/coach/album-3.jpg"]',
   1, '2026-09-28 10:05:00', 1, '2026-09-28 10:05:00'),
  (1856739201475237003, '林教练',
   NULL, '另一位同名教练，用于验证重名放行。', '13800000003',
   NULL,
   1, '2026-09-28 10:10:00', 1, '2026-09-28 10:10:00');

-- ---------------------------------------------------------------------------
-- 3.4 课程 t_course
--     平台级、【不归属门店】（无 store_id）；名称全平台唯一；无状态、无展示排序
--     课种四类：1团课 2精品课 3私教课 4特色课（「私教课」枚举保留但一期不使用）
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS `t_course`;
CREATE TABLE `t_course` (
  `id`          bigint unsigned NOT NULL COMMENT '课程ID',
  `name`        varchar(64)   NOT NULL COMMENT '课程名称（全平台唯一）',
  `course_type` tinyint       NOT NULL COMMENT '课种：1团课 2精品课 3私教课 4特色课',
  `cover_url`   varchar(255)           DEFAULT NULL COMMENT '课程封面URL（单图）',
  `intro`       varchar(1024)          DEFAULT NULL COMMENT '课程介绍',
  `difficulty`  tinyint       NOT NULL COMMENT '课程难度：1~5星',
  `create_by`   bigint unsigned        DEFAULT NULL COMMENT '创建人ID',
  `create_time` datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`   bigint unsigned        DEFAULT NULL COMMENT '更新人ID',
  `update_time` datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_course_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='课程基础信息表';

INSERT INTO `t_course`
  (`id`, `name`, `course_type`, `cover_url`, `intro`, `difficulty`, `create_by`, `create_time`, `update_by`, `update_time`)
VALUES
  (1856739201475235801, '哈他瑜伽',       1, 'https://cdn.example.com/course/hata.jpg',     '以体式与呼吸配合为主的经典课程，适合初学者建立基础。', 2, 1, '2026-09-20 10:12:33', 1, '2026-09-21 09:03:11'),
  (1856739201475235802, '流瑜伽',         2, 'https://cdn.example.com/course/vinyasa.jpg',  '体式之间以呼吸串联，节奏连贯流畅。',                   3, 1, '2026-09-20 10:15:02', 1, '2026-09-22 08:41:07'),
  (1856739201475235803, '空中瑜伽',       4, 'https://cdn.example.com/course/aerial.jpg',   '借助吊床完成体式，需要一定基础。',                     4, 1, '2026-09-20 10:25:39', 1, '2026-09-20 10:25:39'),
  (1856739201475235804, '普拉提器械私教', 3, 'https://cdn.example.com/course/reformer.jpg', '一对一器械课程，按学员情况定制。',                     4, 1, '2026-09-20 10:34:20', 1, '2026-09-20 10:34:20');

-- ---------------------------------------------------------------------------
-- 3.5 排课 t_schedule
--     新增：course_type（课种快照）、coach_id、classroom_id、schedule_date（上课日期）、min_persons
--     改名：capacity → max_persons、booking_count → booked_persons
--     状态：1 待上架 / 2 已上架 / 3 已取消（「已结束」按 end_time 推导，不落库）
--     日期筛选一律走 schedule_date；课种筛选一律走 course_type（快照）
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS `t_schedule`;
CREATE TABLE `t_schedule` (
  `id`             bigint unsigned NOT NULL COMMENT '排课ID',
  `store_id`       bigint unsigned NOT NULL COMMENT '门店ID',
  `course_id`      bigint unsigned NOT NULL COMMENT '课程ID',
  `course_type`    tinyint      NOT NULL COMMENT '课种快照：1团课 2精品课 3私教课 4特色课（由课程复制）',
  `coach_id`       bigint unsigned NOT NULL COMMENT '教练ID',
  `classroom_id`   bigint unsigned NOT NULL COMMENT '教室ID（其 store_id 必须等于本行 store_id）',
  `schedule_date`  date         NOT NULL COMMENT '上课日期（由 start_time 推导，筛选按本列）',
  `start_time`     datetime     NOT NULL COMMENT '开始时间（含日期）',
  `end_time`       datetime     NOT NULL COMMENT '结束时间（须与开始时间同一自然日）',
  `max_persons`    int          NOT NULL COMMENT '最大人数（>0）',
  `min_persons`    int          NOT NULL DEFAULT 2 COMMENT '最低开课人数（1<=min<=max；一期不做自动取消）',
  `booked_persons` int          NOT NULL DEFAULT 0 COMMENT '已预约人数（未取消预约的 persons 总和，由预约事务维护）',
  `status`         tinyint      NOT NULL DEFAULT 1 COMMENT '状态：1待上架 2已上架 3已取消',
  `create_by`      bigint unsigned       DEFAULT NULL COMMENT '创建人ID',
  `create_time`    datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`      bigint unsigned       DEFAULT NULL COMMENT '更新人ID',
  `update_time`    datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_schedule_store_type_date` (`store_id`, `course_type`, `schedule_date`),
  KEY `idx_schedule_coach_time`      (`coach_id`, `start_time`),
  KEY `idx_schedule_classroom`       (`classroom_id`),
  KEY `idx_schedule_course`          (`course_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='排课信息表';

-- 示例排课：用 CURDATE() 相对日期，保证导入当天就能演示【四种状态】
--   ① status=2 明天 10:00  → 已上架未结束 → 用户端显示「可约」
--   ② status=2 明天 19:00  → 已上架未结束 → 用户端显示「可约」
--   ③ status=1 后天 14:00  → 待上架       → 用户端【不可见】
--   ④ status=2 昨天 10:00  → 已上架已结束 → 用户端显示「已结束」（不可约）
--   ⑤ status=3 后天 16:00  → 已取消       → 用户端显示「已取消」（不可约）
INSERT INTO `t_schedule`
  (`id`, `store_id`, `course_id`, `course_type`, `coach_id`, `classroom_id`,
   `schedule_date`, `start_time`, `end_time`, `max_persons`, `min_persons`, `booked_persons`, `status`,
   `create_by`, `create_time`, `update_by`, `update_time`)
VALUES
  (1856739201475239001, 1856739201475235901, 1856739201475235801, 1, 1856739201475237001, 1856739201475236001,
   DATE_ADD(CURDATE(), INTERVAL 1 DAY),
   CONCAT(DATE_ADD(CURDATE(), INTERVAL 1 DAY), ' 10:00:00'),
   CONCAT(DATE_ADD(CURDATE(), INTERVAL 1 DAY), ' 11:00:00'),
   20, 2, 0, 2, 1, SYSDATE(), 1, SYSDATE()),

  (1856739201475239002, 1856739201475235901, 1856739201475235802, 2, 1856739201475237002, 1856739201475236002,
   DATE_ADD(CURDATE(), INTERVAL 1 DAY),
   CONCAT(DATE_ADD(CURDATE(), INTERVAL 1 DAY), ' 19:00:00'),
   CONCAT(DATE_ADD(CURDATE(), INTERVAL 1 DAY), ' 20:15:00'),
   15, 3, 0, 2, 1, SYSDATE(), 1, SYSDATE()),

  (1856739201475239003, 1856739201475235902, 1856739201475235803, 4, 1856739201475237003, 1856739201475236003,
   DATE_ADD(CURDATE(), INTERVAL 2 DAY),
   CONCAT(DATE_ADD(CURDATE(), INTERVAL 2 DAY), ' 14:00:00'),
   CONCAT(DATE_ADD(CURDATE(), INTERVAL 2 DAY), ' 15:00:00'),
   8, 2, 0, 1, 1, SYSDATE(), 1, SYSDATE()),

  (1856739201475239004, 1856739201475235901, 1856739201475235801, 1, 1856739201475237001, 1856739201475236001,
   DATE_SUB(CURDATE(), INTERVAL 1 DAY),
   CONCAT(DATE_SUB(CURDATE(), INTERVAL 1 DAY), ' 10:00:00'),
   CONCAT(DATE_SUB(CURDATE(), INTERVAL 1 DAY), ' 11:00:00'),
   20, 2, 0, 2, 1, SYSDATE(), 1, SYSDATE()),

  (1856739201475239005, 1856739201475235903, 1856739201475235803, 4, 1856739201475237002, 1856739201475236004,
   DATE_ADD(CURDATE(), INTERVAL 2 DAY),
   CONCAT(DATE_ADD(CURDATE(), INTERVAL 2 DAY), ' 16:00:00'),
   CONCAT(DATE_ADD(CURDATE(), INTERVAL 2 DAY), ' 17:00:00'),
   10, 2, 0, 3, 1, SYSDATE(), 1, SYSDATE());

-- ============================================================================
-- 3.6 会员与登录信息 t_member（二期：用户端注册／登录／鉴权）
--     唯一依据：会员管理详细设计 §1.2.1 字段定义、§1.3.1 建表语句
--     账号与会员【共用同一行】：password 为空表示「有档案但未注册」，
--     用户端注册／忘记密码才写密码哈希，管理端会员管理不碰登录字段。
--     没有 deleted 列 —— 与其余业务表一致，删除一律物理删除。
-- ============================================================================
DROP TABLE IF EXISTS `t_member`;
CREATE TABLE `t_member` (
  `id`          bigint unsigned NOT NULL COMMENT '会员ID',
  `wechat_openid` varchar(64) DEFAULT NULL COMMENT '微信openid（三期预留）',
  `wechat_unionid` varchar(64) DEFAULT NULL COMMENT '微信unionid（三期预留）',
  `last_login_time` datetime DEFAULT NULL COMMENT '最后登录时间',
  `phone`       varchar(32)  NOT NULL COMMENT '手机号',
  `password` varchar(100) DEFAULT NULL COMMENT '密码（BCrypt；为空表示未注册）',
  `phone_last4` varchar(4)  NOT NULL COMMENT '手机号后4位',
  `name`        varchar(64)  NOT NULL COMMENT '会员显示名',
  `gender`      tinyint      NOT NULL DEFAULT 0 COMMENT '性别：0未知 1男 2女',
  `level`       tinyint      NOT NULL DEFAULT 0 COMMENT '会员等级',
  `create_by`   bigint unsigned DEFAULT NULL COMMENT '创建人ID',
  `create_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`   bigint unsigned DEFAULT NULL COMMENT '更新人ID',
  `update_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_member_phone` (`phone`),
  INDEX `idx_member_phone_last4` (`phone_last4`),
  UNIQUE KEY `uk_member_wechat_openid` (`wechat_openid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会员与登录信息表';

-- 种子会员（小程序端可直接登录）：
--   手机号 / 密码：13800138000 / yoga1234（下面存的是 BCrypt 哈希，明文不落库）
--   显示名 忻会员8000 = 忻会员 + 手机号后 4 位；等级 0 = V0 普通会员
--   last_login_time 留空，首次登录成功后由后端刷新（用户端接口详细设计 §4.5）
--   ⚠️ 演示账号，上线前请删除或改密码；换成别的密码时用 BCryptPasswordEncoder 生成哈希
INSERT INTO `t_member`
  (`id`, `wechat_openid`, `wechat_unionid`, `last_login_time`, `phone`, `password`, `phone_last4`, `name`,
   `gender`, `level`, `create_by`, `create_time`, `update_by`, `update_time`)
VALUES
  (1856739201475235902, NULL, NULL, NULL, '13800138000',
   '$2a$10$3STsWqV/E3dCLbLtS0qy3OznVw1eBCbP.94Sb.Ny5kAothF1QwMdy', '8000', '忻会员8000',
   0, 0, 1, '2026-10-12 09:00:00', 1, '2026-10-12 09:00:00');

-- ============================================================================
-- 3.7 会员卡 t_member_card（会员卡管理）
--     唯一依据：会员卡管理详细设计 §1.2.1 字段定义、§1.3.1 建表语句
--     卡号 = yyyyMMdd + 开卡门店编号(4) + HHmmss，共 18 位，由服务端按 Asia/Shanghai 生成；
--     uk_card_no 是「同门店同秒最多一张卡」的最终兜底。
--     开卡前（activate_time 为空）不写有效期起止；停卡只写停卡时间与原因，保留剩余次数与有效期。
--     没有 deleted 列 —— 与其余业务表一致，删除一律物理删除。
-- ============================================================================
DROP TABLE IF EXISTS `t_member_card`;
CREATE TABLE `t_member_card` (
  `id`                 bigint unsigned NOT NULL COMMENT '会员卡ID',
  `member_id`          bigint unsigned NOT NULL COMMENT '所属会员ID',
  `open_store_id`      bigint unsigned NOT NULL COMMENT '开卡门店ID（卡号里的门店编号取自它）',
  `card_no`            varchar(64)  NOT NULL COMMENT '卡号',
  `card_name`          varchar(64)  NOT NULL COMMENT '卡名称',
  `card_type`          tinyint      NOT NULL COMMENT '卡类型：1次数卡 2年卡',
  `course_scope`       tinyint      NOT NULL COMMENT '适用课种范围：1团课类 2私教课',
  `store_id`           bigint unsigned DEFAULT NULL COMMENT '适用门店（次数卡必填，年卡为空）',
  `purchase_times`     int          DEFAULT NULL COMMENT '购买次数（仅次数卡）',
  `gift_times`         int          DEFAULT NULL COMMENT '赠送次数（仅次数卡）',
  `remaining_times`    int          DEFAULT NULL COMMENT '剩余次数（仅次数卡，初值=购买+赠送）',
  `valid_period_unit`  tinyint      DEFAULT NULL COMMENT '有效期单位：1月 2年（开卡前为空）',
  `valid_period_count` int          DEFAULT NULL COMMENT '有效期数量（次数卡3/6/12月；年卡固定1年）',
  `valid_start_time`   datetime     DEFAULT NULL COMMENT '有效期起（开卡时写入，开卡日00:00:00）',
  `valid_end_time`     datetime     DEFAULT NULL COMMENT '有效期止（开卡时写入，含首尾日）',
  `activate_time`      datetime     DEFAULT NULL COMMENT '开卡时间',
  `stop_time`          datetime     DEFAULT NULL COMMENT '停卡时间',
  `stop_reason`        varchar(255) DEFAULT NULL COMMENT '停卡原因',
  `create_by`          bigint unsigned DEFAULT NULL COMMENT '创建人ID',
  `create_time`        datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`          bigint unsigned DEFAULT NULL COMMENT '更新人ID',
  `update_time`        datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_card_no` (`card_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会员卡表';

-- 示例会员卡（3 条分别覆盖「待开卡」「使用中」「已停卡」三种展示形态）：
--   卡号里的门店编号必须与 t_store.store_no 一致；有效期见会员卡管理详细设计 §1.2.3
INSERT INTO `t_member_card`
  (`id`, `member_id`, `open_store_id`, `card_no`, `card_name`, `card_type`, `course_scope`, `store_id`,
   `purchase_times`, `gift_times`, `remaining_times`, `valid_period_unit`, `valid_period_count`,
   `valid_start_time`, `valid_end_time`, `activate_time`, `stop_time`, `stop_reason`,
   `create_by`, `create_time`, `update_by`, `update_time`)
VALUES
  -- 次数卡 · 待开卡：剩余次数 = 购买 10 + 赠送 2，未写有效期
  (1856739201475235911, 1856739201475235902, 1856739201475235901, '202610030001100000', '普拉提次卡',
   1, 1, 1856739201475235901, 10, 2, 12, NULL, 12,
   NULL, NULL, NULL, NULL, NULL,
   1, '2026-10-03 10:00:00', 1, '2026-10-03 10:00:00'),

  -- 次数卡 · 使用中：12 个月 = 360 天，含首尾日 → 2026-10-05 00:00:00 ~ 2027-09-29 23:59:59
  (1856739201475235912, 1856739201475235902, 1856739201475235902, '202610050002093000', '瑜伽团课次卡',
   1, 1, 1856739201475235902, 20, 2, 22, 1, 12,
   '2026-10-05 00:00:00', '2027-09-29 23:59:59', '2026-10-05 09:30:00', NULL, NULL,
   1, '2026-10-05 09:00:00', 1, '2026-10-05 09:30:00'),

  -- 年卡 · 已停卡：365 天，含首尾日 → 2026-10-06 00:00:00 ~ 2027-10-05 23:59:59
  (1856739201475235913, 1856739201475235902, 1856739201475235903, '202610060003101500', '私教年卡',
   2, 2, NULL, NULL, NULL, NULL, 2, 1,
   '2026-10-06 00:00:00', '2027-10-05 23:59:59', '2026-10-06 10:15:00', '2026-10-08 09:00:00', '客户申请停卡',
   1, '2026-10-06 10:00:00', 1, '2026-10-08 09:00:00');

-- ============================================================================
-- 3.8 课程预约单 t_reservation（用户端预约课程／取消预约／预约签到／我的预约）
--     唯一依据：预约管理详细设计 §1.2.1 字段定义、§1.3.1 建表语句
--     状态：1 已预约 / 2 已取消 / 3 已签到；课程结束不改变预约状态
--     request_id 是客户端提交幂等标识，uk_member_request 保证重试不重复下单；
--     不能对 (member_id, schedule_id) 建全量唯一约束 —— 取消后允许重新预约，
--     未取消预约的唯一性由会员行锁串行校验。
--     没有 deleted 列 —— 与其余业务表一致，删除一律物理删除。
-- ============================================================================
DROP TABLE IF EXISTS `t_reservation`;
CREATE TABLE `t_reservation` (
  `id`               bigint unsigned NOT NULL COMMENT '预约ID',
  `request_id`       varchar(36)  NOT NULL COMMENT '请求幂等标识',
  `member_id`        bigint unsigned NOT NULL COMMENT '会员ID',
  `schedule_id`      bigint unsigned NOT NULL COMMENT '排课ID',
  `start_time`       datetime     NOT NULL COMMENT '开始时间（预约时的排课快照）',
  `end_time`         datetime     NOT NULL COMMENT '结束时间（预约时的排课快照）',
  `persons`          int          NOT NULL COMMENT '预约人数',
  `member_card_id`   bigint unsigned NOT NULL COMMENT '会员卡ID',
  `status`           tinyint      NOT NULL DEFAULT 1 COMMENT '状态：1已预约 2已取消 3已签到',
  `reservation_time` datetime     NOT NULL COMMENT '预约时间',
  `cancel_time`      datetime     DEFAULT NULL COMMENT '取消时间',
  `cancel_reason`    tinyint      DEFAULT NULL COMMENT '取消原因：1会员主动取消 2排课取消',
  `check_in_time`    datetime     DEFAULT NULL COMMENT '签到时间',
  `create_by`        bigint unsigned DEFAULT NULL COMMENT '创建人ID',
  `create_time`      datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`        bigint unsigned DEFAULT NULL COMMENT '更新人ID',
  `update_time`      datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_member_request` (`member_id`, `request_id`),
  KEY `idx_reservation_member_status` (`member_id`, `status`),
  KEY `idx_reservation_schedule` (`schedule_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='课程预约单';

-- ============================================================================
-- 3.9 会员卡次数流水 t_member_card_transaction（预约扣次／取消返次）
--     唯一依据：预约管理详细设计 §1.2.3 字段定义、§1.3.1 建表语句
--     change_times 扣次为负数、返次为正数；before/after 为变动前后的剩余次数；
--     uk_reservation_type 保证同一预约的扣次、返次流水分别至多一笔。
--     本表只追加，不修改、不删除。
-- ============================================================================
DROP TABLE IF EXISTS `t_member_card_transaction`;
CREATE TABLE `t_member_card_transaction` (
  `id`             bigint unsigned NOT NULL COMMENT '流水ID',
  `member_card_id` bigint unsigned NOT NULL COMMENT '会员卡ID',
  `member_id`      bigint unsigned NOT NULL COMMENT '会员ID',
  `reservation_id` bigint unsigned NOT NULL COMMENT '预约ID',
  `change_type`    tinyint      NOT NULL COMMENT '类型：1预约扣次 2取消返次',
  `persons`        int          NOT NULL COMMENT '预约人数',
  `change_times`   int          NOT NULL COMMENT '变动次数（扣次为负、返次为正）',
  `before_times`   int          NOT NULL COMMENT '变动前剩余次数',
  `after_times`    int          NOT NULL COMMENT '变动后剩余次数',
  `create_time`    datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '变动时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_reservation_type` (`reservation_id`, `change_type`),
  KEY `idx_transaction_card` (`member_card_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会员卡次数流水';

-- ============================================================================
-- 4. 暂不建的表
--    旧预约表 t_booking、私教排班、营业时间登记、教练×门店关联、教练技能、
--    会员等级/权益、优惠券、礼包、积分、合同、体测、公告、消息、活动
--    —— 全部不在本期范围（见 docs/需求文档/业务规则.md 的 BR-全局-005）。
--    注：会员表 t_member 已在 §3.6 落地，会员卡 t_member_card 已在 §3.7 落地，
--    预约单与会员卡扣次／返次流水已在 §3.8、§3.9 落地。
-- ============================================================================
