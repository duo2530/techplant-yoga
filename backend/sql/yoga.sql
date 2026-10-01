-- ============================================================================
-- 瑜伽普拉提 · 一期业务库脚本
-- 版本：v2.0（重置版）   日期：2026-10-01
--
-- 用法：在已经导入若依基础库（ry_20260417.sql + quartz.sql）的库上执行本脚本。
--       本脚本只负责「一期业务表 + 业务菜单 + 业务字典」，不涉及 sys_* 的结构。
--
-- 依据：docs/详细设计/ 下的 7 份详细设计（总览 / 门店 / 教室 / 教练 / 课程 / 排课 / 用户端接口）
--
-- 六条硬口径（与旧脚本的差别）：
--   1. 一期 5 张业务表一律【物理删除】—— 不建 `deleted` 列、不用 @TableLogic
--   2. 门店「所在区域」存【行政区划 code】，区名由字典 `store_region` 翻译
--   3. 排课表保存【课种快照 course_type】与【上课日期 schedule_date】两个派生列
--   4. 排课状态：1 待上架 / 2 已上架 / 3 已取消；「已结束」按 end_time 推导，不落库
--   5. 示例排课用 CURDATE() 相对日期生成，保证任何时候导入都能演示「四种状态」
--   6. 一期不启用预约：本脚本【不含 t_booking】，也【不含预约管理菜单】
--
-- 业务菜单放在独立的一级目录「经营基础」下，与若依自带的「系统管理」目录互不干扰。
-- ============================================================================

SET NAMES utf8mb4;

-- ============================================================================
-- 1. 菜单：一级目录「经营基础」+ 五个业务菜单
--    （只清理 2000~2006，不动若依自带的「系统管理」那套菜单）
-- ============================================================================

DELETE FROM `sys_role_menu` WHERE `menu_id` IN (2000, 2001, 2002, 2003, 2004, 2005, 2006);
DELETE FROM `sys_menu`      WHERE `menu_id` IN (2000, 2001, 2002, 2003, 2004, 2005, 2006);

-- 一级目录：经营基础
INSERT INTO `sys_menu` VALUES ('2000', '经营基础', '0',    '5', 'operation',  NULL,            '', '', 1, 0, 'M', '0', '0', '', 'education', 'admin', SYSDATE(), '', NULL, '经营基础目录');

-- 二级菜单（component 对应 frontend/pc/src/views/<name>/index.vue）
INSERT INTO `sys_menu` VALUES ('2001', '门店管理', '2000', '1', 'store',     'store/index',     '', '', 1, 0, 'C', '0', '0', '', 'shopping',  'admin', SYSDATE(), '', NULL, '门店管理菜单');
INSERT INTO `sys_menu` VALUES ('2002', '教室管理', '2000', '2', 'classroom', 'classroom/index', '', '', 1, 0, 'C', '0', '0', '', 'tree',      'admin', SYSDATE(), '', NULL, '教室管理菜单');
INSERT INTO `sys_menu` VALUES ('2003', '教练管理', '2000', '3', 'coach',     'coach/index',     '', '', 1, 0, 'C', '0', '0', '', 'user',      'admin', SYSDATE(), '', NULL, '教练管理菜单');
INSERT INTO `sys_menu` VALUES ('2004', '课程管理', '2000', '4', 'course',    'course/index',    '', '', 1, 0, 'C', '0', '0', '', 'education', 'admin', SYSDATE(), '', NULL, '课程管理菜单');
INSERT INTO `sys_menu` VALUES ('2005', '排课管理', '2000', '5', 'schedule',  'schedule/index',  '', '', 1, 0, 'C', '0', '0', '', 'date',      'admin', SYSDATE(), '', NULL, '排课管理菜单');

-- 绑定若依内置管理员角色（role_id = 1）
INSERT INTO `sys_role_menu` VALUES ('1', '2000');
INSERT INTO `sys_role_menu` VALUES ('1', '2001');
INSERT INTO `sys_role_menu` VALUES ('1', '2002');
INSERT INTO `sys_role_menu` VALUES ('1', '2003');
INSERT INTO `sys_role_menu` VALUES ('1', '2004');
INSERT INTO `sys_role_menu` VALUES ('1', '2005');

-- ============================================================================
-- 2. 字典：门店「所在区域」（上海市 16 个市辖区的行政区划代码）
--    前端下拉与后端「code → 区名」翻译都以这里为唯一来源。
--    ⚠️ 代码里的 16 条属于暂定，上线前请逐条核对。
-- ============================================================================

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

-- ============================================================================
-- 3. 业务表
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 3.1 门店 t_store
--     无状态、无经营类型、无省市区三级 code（只有区级 region_code）、无 deleted
--     既有表的 region / province_code / city_code / district_code / business_type /
--     status / deleted 全部不再保留
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS `t_store`;
CREATE TABLE `t_store` (
  `id`             bigint unsigned NOT NULL COMMENT '门店ID',
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
  UNIQUE KEY `uk_store_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='门店基础信息表';

INSERT INTO `t_store`
  (`id`, `name`, `store_type`, `region_code`, `phone`, `business_hours`, `address`, `image_url`, `create_by`, `create_time`, `update_by`, `update_time`)
VALUES
  (1856739201475235901, '徐汇店',     1, '310104', '021-12345678', '周一至周日 09:00-22:00', '漕溪北路 88 号',   'https://cdn.example.com/store/xuhui.jpg',  1, '2026-09-20 09:00:00', 1, '2026-09-20 09:00:00'),
  (1856739201475235902, '静安精品店', 2, '310106', '021-87654321', '周一至周日 10:00-21:00', '愚园路 168 号',    NULL,                                       1, '2026-09-20 09:10:00', 1, '2026-09-20 09:10:00'),
  (1856739201475235903, '浦东店',     1, '310115', '021-66668888', '周一至周日 08:30-22:00', '世纪大道 1000 号', NULL,                                       1, '2026-09-20 09:20:00', 1, '2026-09-20 09:20:00');

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
--     平台级、不关联门店；名称允许重名（不建唯一索引）；无状态、无头衔/金牌/排序
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
  `booked_persons` int          NOT NULL DEFAULT 0 COMMENT '已预约人数（一期固定0）',
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
-- 4. 一期不建的表
--    会员、会员卡、预约（t_booking）、私教排班、营业时间登记、
--    教练×门店关联、教练技能、会员等级/权益、优惠券、礼包、积分、合同、体测、
--    公告、消息、活动 —— 全部不在本期范围（见 docs/需求文档/业务规则.md 的 BR-全局-005）。
-- ============================================================================
