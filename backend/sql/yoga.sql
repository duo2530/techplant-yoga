DELETE FROM `sys_role_menu` WHERE `menu_id` IN (2000, 2001, 2002, 2003);
DELETE FROM `sys_menu` WHERE `menu_id` IN (2000, 2001, 2002, 2003);

-- 一级目录：经营基础（门店 / 课程 / 教练 / 排班 / 预约 后续都挂在这个目录下）
insert into sys_menu values('2000', '经营基础', '0',    '5', 'operation', null,           '', '', 1, 0, 'M', '0', '0', '',          'education', 'admin', sysdate(), '', null, '经营基础目录');
-- 二级菜单：课程管理（路由 /operation/course，组件 src/views/course/index.vue）
insert into sys_menu values('2001', '课程管理', '2000', '1', 'course',    'course/index', '', '', 1, 0, 'C', '0', '0', '',          'list',      'admin', sysdate(), '', null, '课程管理菜单');
-- 二级菜单：教练管理（路由 /operation/coach，组件 src/views/coach/index.vue）
insert into sys_menu values('2002', '教练管理', '2000', '2', 'coach',     'coach/index',  '', '', 1, 0, 'C', '0', '0', '',          'user',      'admin', sysdate(), '', null, '教练管理菜单');
-- 二级菜单：门店管理（路由 /operation/store，组件 src/views/store/index.vue）
insert into sys_menu values('2003', '门店管理', '2000', '3', 'store',     'store/index',  '', '', 1, 0, 'C', '0', '0', '',          'shopping',  'admin', sysdate(), '', null, '门店管理菜单');

-- 角色绑定：role_id = 1 是若依内置管理员（它的菜单本来就不受 sys_role_menu 限制，
-- 这里补上是为了让角色-菜单关系在「角色管理」页面里可见、可维护）
insert into sys_role_menu values ('1', '2000');
insert into sys_role_menu values ('1', '2001');
insert into sys_role_menu values ('1', '2002');
insert into sys_role_menu values ('1', '2003');

DROP TABLE IF EXISTS `t_store`;
CREATE TABLE `t_store` (
                          `id`             bigint unsigned NOT NULL                          COMMENT '门店ID',
                          `name`           varchar(64)     NOT NULL                          COMMENT '门店名称',
                          `region`         varchar(64)     NOT NULL                          COMMENT '所在区域',
                          `province_code`  varchar(16)     NOT NULL                          COMMENT '省行政区code，用于地图定位',
                          `city_code`      varchar(16)     NOT NULL                          COMMENT '市行政区code，用于地图定位',
                          `district_code`  varchar(16)     NOT NULL                          COMMENT '区/县行政区code，用于地图定位',
                          `address`        varchar(255)    NOT NULL                          COMMENT '门店地址；用户端地址与门店地址共用',
                          `phone`          varchar(32)     NOT NULL                          COMMENT '门店电话',
                          `business_type`  tinyint         NOT NULL                          COMMENT '经营类型：1直营连锁 2加盟',
                          `store_type`     tinyint         NOT NULL                          COMMENT '门店类型：1主力店 2精品店',
                          `business_hours` varchar(64)     NOT NULL                          COMMENT '营业时间',
                          `status`         tinyint         NOT NULL DEFAULT 1                COMMENT '门店状态：1启用 0停用',
                          `deleted`        tinyint         NOT NULL DEFAULT 0                COMMENT '逻辑删除：0正常 1已删除',
                          `create_by`      bigint unsigned          DEFAULT NULL             COMMENT '创建人ID',
                          `create_time`    datetime        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                          `update_by`      bigint unsigned          DEFAULT NULL             COMMENT '更新人ID',
                          `update_time`    datetime        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                          PRIMARY KEY (`id`),
                          KEY `idx_name` (`name`),
                          KEY `idx_region_type_status` (`region`, `store_type`, `status`),
                          KEY `idx_business_type_status` (`business_type`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='门店基础信息表';

INSERT INTO `t_store`
(`id`, `name`, `region`, `province_code`, `city_code`, `district_code`, `address`, `phone`, `business_type`, `store_type`, `business_hours`, `status`, `deleted`, `create_by`, `create_time`, `update_by`, `update_time`)
VALUES
    (1856739201475235901, '徐汇店', '上海市徐汇区', '310000', '310100', '310104', '漕溪北路 88 号', '021-12345678', 1, 1, '周一至周日 09:00-22:00', 1, 0, 1, '2026-09-20 09:00:00', 1, '2026-09-20 09:00:00'),
    (1856739201475235902, '静安精品店', '上海市静安区', '310000', '310100', '310106', '愚园路 168 号', '021-87654321', 2, 2, '周一至周日 10:00-21:00', 1, 0, 1, '2026-09-20 09:10:00', 1, '2026-09-20 09:10:00');

DROP TABLE IF EXISTS `t_course`;
CREATE TABLE `t_course` (
                            `id`           bigint unsigned NOT NULL                          COMMENT '课程ID',
                            `name`         varchar(64)     NOT NULL                          COMMENT '课程名称',
                            `type`         tinyint         NOT NULL                          COMMENT '课种：1团课 2精品课 3私教课 4特色课',
                            `difficulty`   tinyint         NOT NULL DEFAULT 1                COMMENT '课程难度：1~5 星',
                            `cover_url`    varchar(255)             DEFAULT NULL             COMMENT '课程封面图URL',
                            `intro`        text                                              COMMENT '课程介绍',
                            `duration_min` smallint unsigned        DEFAULT NULL             COMMENT '单节时长（分钟）',
                            `sort_no`      int             NOT NULL DEFAULT 0                COMMENT '展示排序，值越小越靠前',
                            `status`       tinyint         NOT NULL DEFAULT 1                COMMENT '课程状态：1启用 0停用',
                            `deleted`      tinyint         NOT NULL DEFAULT 0                COMMENT '逻辑删除：0正常 1已删除',
                            `create_by`    bigint unsigned          DEFAULT NULL             COMMENT '创建人ID',
                            `create_time`  datetime        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                            `update_by`    bigint unsigned          DEFAULT NULL             COMMENT '更新人ID',
                            `update_time`  datetime        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                            PRIMARY KEY (`id`),
                            KEY `idx_type_status` (`type`, `status`),
                            KEY `idx_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='课程基础信息表';

INSERT INTO `t_course`
(`id`, `name`, `type`, `difficulty`, `cover_url`, `intro`, `duration_min`, `sort_no`, `status`, `deleted`, `create_by`, `create_time`, `update_by`, `update_time`)
VALUES
    (1856739201475235801, '哈他瑜伽',   1, 2, 'https://cdn.example.com/course/hata.jpg',      '以体式与呼吸配合为主的经典课程，适合初学者建立基础。', 60,  10, 1, 0, 1, '2026-09-20 10:12:33', 1, '2026-09-21 09:03:11'),
    (1856739201475235802, '流瑜伽',     2, 3, 'https://cdn.example.com/course/vinyasa.jpg',   '体式之间以呼吸串联，节奏连贯流畅。',                   75,  20, 1, 0, 1, '2026-09-20 10:15:02', 1, '2026-09-22 08:41:07'),
    (1856739201475235803, '阴瑜伽',     2, 1, 'https://cdn.example.com/course/yin.jpg',       '长时间保持体式，作用于筋膜与关节。',                   90,  30, 1, 0, 1, '2026-09-20 10:18:44', 1, '2026-09-20 10:18:44'),
    (1856739201475235804, '普拉提垫上', 1, 2, 'https://cdn.example.com/course/mat.jpg',       '核心控制与呼吸配合的垫上训练。',                       50,  40, 1, 0, 1, '2026-09-20 10:22:10', 1, '2026-09-20 10:22:10'),
    (1856739201475235805, '空中瑜伽',   4, 4, 'https://cdn.example.com/course/aerial.jpg',    '借助吊床完成体式，需要一定基础。',                     60,  50, 1, 0, 1, '2026-09-20 10:25:39', 1, '2026-09-20 10:25:39'),
    (1856739201475235806, '孕产瑜伽',   4, 1, 'https://cdn.example.com/course/prenatal.jpg',  '面向孕产期人群的温和课程，需教练评估后参加。',         60,  60, 1, 0, 1, '2026-09-20 10:28:57', 1, '2026-09-20 10:28:57'),
    (1856739201475235807, '阿斯汤加',   2, 5, 'https://cdn.example.com/course/ashtanga.jpg',  '固定序列的进阶课程，强度较高。',                       90,  70, 0, 0, 1, '2026-09-20 10:31:12', 1, '2026-09-22 11:02:45'),
    (1856739201475235808, '普拉提器械', 3, 4, 'https://cdn.example.com/course/reformer.jpg',  '一对一器械课程，按学员情况定制。',                     55,  80, 1, 0, 1, '2026-09-20 10:34:20', 1, '2026-09-20 10:34:20'),
    (1856739201475235809, '私教体验课', 3, 1, 'https://cdn.example.com/course/trial.jpg',     '私教体验课程，用于首次到店评估。',                     45,  90, 1, 0, 1, '2026-09-20 10:37:05', 1, '2026-09-20 10:37:05'),
    (1856739201475235810, '肩颈理疗',   4, 1, NULL,                                          '针对久坐人群的肩颈放松课程。',                         60, 100, 1, 0, 1, '2026-09-20 10:40:33', 1, '2026-09-20 10:40:33'),
    (1856739201475235811, '冥想放松',   4, 1, NULL,                                          '呼吸练习与冥想引导，用于课后放松。',                   30, 110, 1, 0, 1, '2026-09-20 10:43:18', 1, '2026-09-20 10:43:18'),
    (1856739201475235812, '亲子瑜伽',   4, 2, NULL,                                          '家长与孩子共同参与的课程。',                           45, 120, 1, 0, 1, '2026-09-20 10:46:02', 1, '2026-09-20 10:46:02'),
    (1856739201475235813, '已删除课程', 1, 1, NULL,                                          '仅用于验证逻辑删除不参与查询。',                       NULL, 130, 1, 1, 1, '2026-09-20 10:49:41', 1, '2026-09-20 10:49:41');

DROP TABLE IF EXISTS `t_coach`;
CREATE TABLE `t_coach` (
  `id`          bigint unsigned NOT NULL COMMENT '教练ID',
  `intro`       text                                      COMMENT '教练简介',
  `name`        varchar(64) NOT NULL                       COMMENT '教练名称',
  `title`       varchar(64)                              DEFAULT NULL COMMENT '教练头衔',
  `avatar_url`  varchar(255)                             DEFAULT NULL COMMENT '教练头像URL',
  `album_urls`  json                                     DEFAULT NULL COMMENT '教练相册URL数组，最多5张',
  `status`      tinyint NOT NULL DEFAULT 1                COMMENT '教练状态：1启用 0停用',
  `deleted`     tinyint NOT NULL DEFAULT 0                COMMENT '逻辑删除：0正常 1已删除',
  `create_by`   bigint unsigned                          DEFAULT NULL COMMENT '创建人ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`   bigint unsigned                          DEFAULT NULL COMMENT '更新人ID',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_name` (`name`),
  KEY `idx_status_name` (`status`, `name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='教练基础信息表';

INSERT INTO `t_coach`
(`id`, `intro`, `name`, `title`, `avatar_url`, `album_urls`, `status`, `deleted`, `create_by`, `create_time`, `update_by`, `update_time`)
VALUES
    (1856739201475235901, '专注基础体式与呼吸练习。', '林教练', '金牌教练', 'https://cdn.example.com/coach/avatar-1.jpg',
     '["https://cdn.example.com/coach/album-1.jpg", "https://cdn.example.com/coach/album-2.jpg"]', 1, 0, 1, '2026-09-28 10:00:00', 1, '2026-09-28 10:00:00'),
    (1856739201475235902, '擅长普拉提和体态训练。', '周教练', '瑜伽导师', 'https://cdn.example.com/coach/avatar-2.jpg',
     '["https://cdn.example.com/coach/album-3.jpg"]', 1, 0, 1, '2026-09-28 10:05:00', 1, '2026-09-28 10:05:00');
