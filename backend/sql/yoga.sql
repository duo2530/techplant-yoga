-- 在导入若依基础库后执行，重建业务表并初始化数据。

SET NAMES utf8mb4;

-- ----------------------------
-- 初始化-业务菜单
-- ----------------------------

DELETE FROM `sys_role_menu` WHERE `menu_id` IN (2000, 2001, 2002, 2003, 2004, 2005, 2006, 2007, 2008, 2009, 2010);
DELETE FROM `sys_menu`      WHERE `menu_id` IN (2000, 2001, 2002, 2003, 2004, 2005, 2006, 2007, 2008, 2009, 2010);

INSERT INTO `sys_menu` VALUES ('2000', '经营基础', '0',    '5', 'operation',  NULL,            '', '', 1, 0, 'M', '0', '0', '', 'education', 'admin', SYSDATE(), '', NULL, '经营基础目录');

INSERT INTO `sys_menu` VALUES ('2001', '门店管理', '2000', '1', 'store',     'store/index',     '', '', 1, 0, 'C', '0', '0', '', 'shopping',  'admin', SYSDATE(), '', NULL, '门店管理菜单');
INSERT INTO `sys_menu` VALUES ('2002', '教室管理', '2000', '2', 'classroom', 'classroom/index', '', '', 1, 0, 'C', '0', '0', '', 'tree',      'admin', SYSDATE(), '', NULL, '教室管理菜单');
INSERT INTO `sys_menu` VALUES ('2003', '教练管理', '2000', '3', 'coach',     'coach/index',     '', '', 1, 0, 'C', '0', '0', '', 'user',      'admin', SYSDATE(), '', NULL, '教练管理菜单');
INSERT INTO `sys_menu` VALUES ('2004', '课程管理', '2000', '4', 'course',    'course/index',    '', '', 1, 0, 'C', '0', '0', '', 'education', 'admin', SYSDATE(), '', NULL, '课程管理菜单');
INSERT INTO `sys_menu` VALUES ('2005', '排课管理', '2000', '5', 'schedule',  'schedule/index',  '', '', 1, 0, 'C', '0', '0', '', 'date',      'admin', SYSDATE(), '', NULL, '排课管理菜单');
INSERT INTO `sys_menu` VALUES ('2006', '会员管理', '2000', '6', 'member',    'member/index',    '', '', 1, 0, 'C', '0', '0', '', 'peoples',   'admin', SYSDATE(), '', NULL, '会员管理菜单');
INSERT INTO `sys_menu` VALUES ('2007', '会员卡管理', '2000', '7', 'membercard', 'membercard/index', '', '', 1, 0, 'C', '0', '0', '', 'money', 'admin', SYSDATE(), '', NULL, '会员卡管理菜单');
INSERT INTO `sys_menu` VALUES ('2008', '预约管理', '2000', '8', 'reservation', 'reservation/index', '', '', 1, 0, 'C', '0', '0', '', 'date', 'admin', SYSDATE(), '', NULL, '预约管理菜单');

-- ----------------------------
-- 初始化-管理员菜单绑定
-- ----------------------------
INSERT INTO `sys_role_menu` VALUES ('1', '2000');
INSERT INTO `sys_role_menu` VALUES ('1', '2001');
INSERT INTO `sys_role_menu` VALUES ('1', '2002');
INSERT INTO `sys_role_menu` VALUES ('1', '2003');
INSERT INTO `sys_role_menu` VALUES ('1', '2004');
INSERT INTO `sys_role_menu` VALUES ('1', '2005');
INSERT INTO `sys_role_menu` VALUES ('1', '2006');
INSERT INTO `sys_role_menu` VALUES ('1', '2007');
INSERT INTO `sys_role_menu` VALUES ('1', '2008');

-- ----------------------------
-- 初始化-门店区域字典
-- ----------------------------

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

-- ----------------------------
-- 初始化-会员等级字典
-- ----------------------------

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

-- ----------------------------
-- 1、门店表
-- ----------------------------
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

-- ----------------------------
-- 初始化-门店表数据
-- ----------------------------
INSERT INTO `t_store`
  (`id`, `store_no`, `name`, `store_type`, `region_code`, `phone`, `business_hours`, `address`, `image_url`, `create_by`, `create_time`, `update_by`, `update_time`)
VALUES
  (1856739201475235901, '0001', '徐汇店',     1, '310104', '021-12345678', '周一至周日 09:00-22:00', '漕溪北路 88 号',   'https://cdn.example.com/store/xuhui.jpg',  1, '2026-09-20 09:00:00', 1, '2026-09-20 09:00:00'),
  (1856739201475235902, '0002', '静安精品店', 2, '310106', '021-87654321', '周一至周日 10:00-21:00', '愚园路 168 号',    NULL,                                       1, '2026-09-20 09:10:00', 1, '2026-09-20 09:10:00'),
  (1856739201475235903, '0003', '浦东店',     1, '310115', '021-66668888', '周一至周日 08:30-22:00', '世纪大道 1000 号', NULL,                                       1, '2026-09-20 09:20:00', 1, '2026-09-20 09:20:00');

-- ----------------------------
-- 2、门店编号计数器表
-- ----------------------------
DROP TABLE IF EXISTS `t_store_no_counter`;
CREATE TABLE `t_store_no_counter` (
  `id`      tinyint unsigned NOT NULL COMMENT '固定为1',
  `last_no` int unsigned     NOT NULL COMMENT '已分配的最大门店编号',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='门店编号计数器';

-- ----------------------------
-- 初始化-门店编号计数器表数据
-- ----------------------------
INSERT INTO `t_store_no_counter` (`id`, `last_no`) VALUES (1, 3);

-- ----------------------------
-- 3、教室表
-- ----------------------------
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

-- ----------------------------
-- 初始化-教室表数据
-- ----------------------------
INSERT INTO `t_classroom` (`id`, `store_id`, `name`, `create_by`, `create_time`, `update_by`, `update_time`)
VALUES
  (1856739201475236001, 1856739201475235901, '瑜伽团课大教室',   1, '2026-09-20 09:30:00', 1, '2026-09-20 09:30:00'),
  (1856739201475236002, 1856739201475235901, '普拉提器械教室',   1, '2026-09-20 09:31:00', 1, '2026-09-20 09:31:00'),
  (1856739201475236003, 1856739201475235902, '精品小班教室',     1, '2026-09-20 09:32:00', 1, '2026-09-20 09:32:00'),
  (1856739201475236004, 1856739201475235903, '团课大教室',       1, '2026-09-20 09:33:00', 1, '2026-09-20 09:33:00');

-- ----------------------------
-- 4、教练表
-- ----------------------------
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

-- ----------------------------
-- 初始化-教练表数据
-- ----------------------------
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

-- ----------------------------
-- 5、课程表
-- ----------------------------
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

-- ----------------------------
-- 初始化-课程表数据
-- ----------------------------
INSERT INTO `t_course`
  (`id`, `name`, `course_type`, `cover_url`, `intro`, `difficulty`, `create_by`, `create_time`, `update_by`, `update_time`)
VALUES
  (1856739201475235801, '哈他瑜伽',       1, 'https://cdn.example.com/course/hata.jpg',     '以体式与呼吸配合为主的经典课程，适合初学者建立基础。', 2, 1, '2026-09-20 10:12:33', 1, '2026-09-21 09:03:11'),
  (1856739201475235802, '流瑜伽',         2, 'https://cdn.example.com/course/vinyasa.jpg',  '体式之间以呼吸串联，节奏连贯流畅。',                   3, 1, '2026-09-20 10:15:02', 1, '2026-09-22 08:41:07'),
  (1856739201475235803, '空中瑜伽',       4, 'https://cdn.example.com/course/aerial.jpg',   '借助吊床完成体式，需要一定基础。',                     4, 1, '2026-09-20 10:25:39', 1, '2026-09-20 10:25:39'),
  (1856739201475235804, '普拉提器械私教', 3, 'https://cdn.example.com/course/reformer.jpg', '一对一器械课程，按学员情况定制。',                     4, 1, '2026-09-20 10:34:20', 1, '2026-09-20 10:34:20');

-- ----------------------------
-- 6、排课表
-- ----------------------------
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

-- ----------------------------
-- 初始化-排课表数据
-- ----------------------------
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

-- ----------------------------
-- 7、会员与登录信息表
-- ----------------------------
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

-- ----------------------------
-- 初始化-会员表数据
-- ----------------------------
-- 示例会员登录：13800138000 / yoga1234
INSERT INTO `t_member`
  (`id`, `wechat_openid`, `wechat_unionid`, `last_login_time`, `phone`, `password`, `phone_last4`, `name`,
   `gender`, `level`, `create_by`, `create_time`, `update_by`, `update_time`)
VALUES
  (1856739201475235902, NULL, NULL, NULL, '13800138000',
   '$2a$10$3STsWqV/E3dCLbLtS0qy3OznVw1eBCbP.94Sb.Ny5kAothF1QwMdy', '8000', '忻会员8000',
   0, 0, 1, '2026-10-12 09:00:00', 1, '2026-10-12 09:00:00');

-- ----------------------------
-- 8、会员卡表
-- ----------------------------
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

-- ----------------------------
-- 初始化-会员卡表数据
-- ----------------------------
INSERT INTO `t_member_card`
  (`id`, `member_id`, `open_store_id`, `card_no`, `card_name`, `card_type`, `course_scope`, `store_id`,
   `purchase_times`, `gift_times`, `remaining_times`, `valid_period_unit`, `valid_period_count`,
   `valid_start_time`, `valid_end_time`, `activate_time`, `stop_time`, `stop_reason`,
   `create_by`, `create_time`, `update_by`, `update_time`)
VALUES
  (1856739201475235911, 1856739201475235902, 1856739201475235901, '202610030001100000', '普拉提次卡',
   1, 1, 1856739201475235901, 10, 2, 12, NULL, 12,
   NULL, NULL, NULL, NULL, NULL,
   1, '2026-10-03 10:00:00', 1, '2026-10-03 10:00:00'),

  (1856739201475235912, 1856739201475235902, 1856739201475235902, '202610050002093000', '瑜伽团课次卡',
   1, 1, 1856739201475235902, 20, 2, 22, 1, 12,
   '2026-10-05 00:00:00', '2027-09-29 23:59:59', '2026-10-05 09:30:00', NULL, NULL,
   1, '2026-10-05 09:00:00', 1, '2026-10-05 09:30:00'),

  (1856739201475235913, 1856739201475235902, 1856739201475235903, '202610060003101500', '私教年卡',
   2, 2, NULL, NULL, NULL, NULL, 2, 1,
   '2026-10-06 00:00:00', '2027-10-05 23:59:59', '2026-10-06 10:15:00', '2026-10-08 09:00:00', '客户申请停卡',
   1, '2026-10-06 10:00:00', 1, '2026-10-08 09:00:00');

-- ----------------------------
-- 9、课程预约单表
-- ----------------------------
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

-- ----------------------------
-- 10、会员卡次数流水表
-- ----------------------------
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
