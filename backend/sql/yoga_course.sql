-- ----------------------------------------------------------------------------
-- 课程管理模块 · 建表脚本
-- 来源：docs/详细设计/详细设计.md v0.1 §1.3.1「建表语句（MySQL 8.0）」
-- 口径：数据库 MySQL 8.0 / InnoDB / utf8mb4；表名 t_ + 业务名（§1.1.1）
--      主键为雪花ID，由应用侧 MyBatis-Plus（IdType.ASSIGN_ID）生成，不使用数据库自增
-- 执行：先建库（当前配置的库名是 ry-vue），再执行本脚本
--      例：mysql -h <host> -P <port> -u root -p ry-vue < backend/sql/yoga_course.sql
-- 幂等：脚本含 DROP TABLE，重复执行会清空既有数据，请勿在生产库执行
-- ----------------------------------------------------------------------------

DROP TABLE IF EXISTS `t_course`;
CREATE TABLE `t_course` (
  `id`           bigint unsigned NOT NULL                          COMMENT '课程ID（雪花ID，应用侧生成）',
  `name`         varchar(64)     NOT NULL                          COMMENT '课程名称',
  `type`         tinyint         NOT NULL                          COMMENT '课程类型：1团课 2精品课 3私教课 4特色课',
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='课程基础信息表';
