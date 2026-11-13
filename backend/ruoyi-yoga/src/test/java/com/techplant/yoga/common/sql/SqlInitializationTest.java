package com.techplant.yoga.common.sql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;
import com.techplant.yoga.common.testcontainers.YogaSchema;

@DisplayName("SQL 初始化脚本（隔离 MySQL：首次导入与重复重建）")
class SqlInitializationTest {
    private static final String IMAGE = "mysql:8.0";

    private static final String DATABASE = "techplant_yoga_init";

    private static final String BUSINESS_SCRIPT = "yoga.sql";

    private static final String QUEUE_CONFIG_MIGRATION = "migrations/20261109-schedule-queue-config.sql";

    private static final String AUTO_CANCEL_JOB_MIGRATION = "migrations/20261109-schedule-auto-cancel-job.sql";

    private static final String MEMBER_CARD_INDEX_MIGRATION = "migrations/20261114-member-card-member-index.sql";

    private static final String MEMBER_CARD_INDEX = "idx_member_card_member_type";

    private static final String AUTO_CANCEL_JOB = "SELECT COUNT(*) FROM sys_job WHERE job_id = 100"
            + " AND invoke_target = 'yogaScheduleTask.cancelUnderstaffed' AND cron_expression = '0 * * * * ?'"
            + " AND misfire_policy = '3' AND concurrent = '1' AND status = '0'";

    private static final long SAMPLE_COACH_ID = 1856739201475237002L;

    private static final long EXTRA_COACH_ID = 9101L;

    @Test
    @DisplayName("首次初始化与重复执行均重建业务表、菜单和普通等级种子数据")
    void initializeAndRebuildBusinessDatabase() throws Exception {
        MySQLContainer<?> container = new MySQLContainer<>(DockerImageName.parse(IMAGE)).withDatabaseName(DATABASE)
                .withUsername("yoga").withPassword("yoga").withCommand("--default-time-zone=+08:00");
        container.start();
        try {
            DataSource dataSource = dataSourceOf(container);
            // 若依基础表由基础库提供，业务表和种子数据由 yoga.sql 初始化。
            YogaSchema.reset(dataSource, "sys_menu", "sys_role_menu", "sys_dict_type", "sys_dict_data", "sys_job");
            executeScript(dataSource, YogaSchema.locateScript(BUSINESS_SCRIPT));
            assertInitialized(dataSource);
            long scheduleCount = count(dataSource, "SELECT COUNT(*) FROM t_schedule");

            execute(dataSource, "UPDATE t_coach SET level = 2 WHERE id = " + SAMPLE_COACH_ID);
            execute(dataSource,
                    "INSERT INTO t_coach (id, name, phone) VALUES (" + EXTRA_COACH_ID + ", '临时教练', '13800000009')");
            execute(dataSource, "DELETE FROM t_schedule");
            execute(dataSource, "DELETE FROM sys_role_menu WHERE menu_id = 2003");
            execute(dataSource, "DELETE FROM sys_menu WHERE menu_id = 2003");
            execute(dataSource, "INSERT INTO sys_menu (menu_id, menu_name, parent_id, menu_type, perms)"
                    + " VALUES (2010, '金牌教练设置', 2003, 'F', 'yoga:coach:level')");
            execute(dataSource, "INSERT INTO sys_role_menu (role_id, menu_id) VALUES (1, 2010)");

            executeScript(dataSource, YogaSchema.locateScript(BUSINESS_SCRIPT));
            assertInitialized(dataSource);
            assertEquals(0, count(dataSource, "SELECT COUNT(*) FROM t_coach WHERE id = " + EXTRA_COACH_ID),
                    "重建移除原有业务数据");
            assertEquals(scheduleCount, count(dataSource, "SELECT COUNT(*) FROM t_schedule"), "重建恢复种子排课");

            execute(dataSource, "ALTER TABLE t_schedule DROP COLUMN queue_stop_minutes, DROP COLUMN queue_limit");
            executeScript(dataSource, YogaSchema.locateScript(QUEUE_CONFIG_MIGRATION));
            executeScript(dataSource, YogaSchema.locateScript(QUEUE_CONFIG_MIGRATION));
            assertQueueConfigColumns(dataSource);
            assertEquals(scheduleCount,
                    count(dataSource,
                            "SELECT COUNT(*) FROM t_schedule WHERE queue_limit = 3 AND queue_stop_minutes = 110"),
                    "增量脚本为已有排课补默认排队配置，重复执行不报错");

            execute(dataSource, "DELETE FROM sys_job WHERE job_id = 100");
            executeScript(dataSource, YogaSchema.locateScript(AUTO_CANCEL_JOB_MIGRATION));
            executeScript(dataSource, YogaSchema.locateScript(AUTO_CANCEL_JOB_MIGRATION));
            assertEquals(1, count(dataSource, AUTO_CANCEL_JOB), "增量脚本补上自动取消任务，重复执行不重复插入");
            execute(dataSource, "UPDATE sys_job SET status = '1' WHERE job_id = 100");
            executeScript(dataSource, YogaSchema.locateScript(AUTO_CANCEL_JOB_MIGRATION));
            assertEquals("1",
                    String.valueOf(queryRow(dataSource, "SELECT status FROM sys_job WHERE job_id = 100").get("status")),
                    "已有任务时增量脚本不覆盖管理员的修改");

            execute(dataSource, "ALTER TABLE t_member_card DROP INDEX " + MEMBER_CARD_INDEX);
            executeScript(dataSource, YogaSchema.locateScript(MEMBER_CARD_INDEX_MIGRATION));
            executeScript(dataSource, YogaSchema.locateScript(MEMBER_CARD_INDEX_MIGRATION));
            assertMemberCardIndex(dataSource);
        } finally {
            container.stop();
        }
    }

    private void assertInitialized(DataSource dataSource) throws Exception {
        assertLevelColumn(dataSource);
        assertQueueConfigColumns(dataSource);
        assertMemberCardIndex(dataSource);
        assertEquals(3, count(dataSource, "SELECT COUNT(*) FROM t_coach"), "样例教练 3 条");
        assertEquals(0, count(dataSource, "SELECT COUNT(*) FROM t_coach WHERE level <> 1"), "样例教练均为普通等级");
        assertNoLevelPermission(dataSource);
        assertEquals(1, count(dataSource, AUTO_CANCEL_JOB), "业务脚本初始化自动取消定时任务");
        assertEquals(9, count(dataSource, "SELECT COUNT(*) FROM sys_menu WHERE menu_id BETWEEN 2000 AND 2008"),
                "业务菜单完整");
        assertEquals(9,
                count(dataSource,
                        "SELECT COUNT(*) FROM sys_role_menu WHERE role_id = 1 AND menu_id BETWEEN 2000 AND 2008"),
                "内置管理员绑定完整");
        assertTrue(count(dataSource, "SELECT COUNT(*) FROM t_schedule WHERE coach_id = " + SAMPLE_COACH_ID) > 0,
                "种子排课关联样例教练");
    }

    private void assertLevelColumn(DataSource dataSource) throws Exception {
        Map<String, Object> column = queryRow(dataSource,
                "SELECT COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT"
                        + " FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_coach'"
                        + " AND COLUMN_NAME = 'level'");
        assertNotEquals(0, column.size(), "t_coach 必须有 level 列");
        assertEquals("tinyint", column.get("column_type"));
        assertEquals("NO", column.get("is_nullable"));
        assertEquals("1", String.valueOf(column.get("column_default")));
    }

    private void assertQueueConfigColumns(DataSource dataSource) throws Exception {
        assertIntColumn(dataSource, "queue_limit", "3");
        assertIntColumn(dataSource, "queue_stop_minutes", "110");
    }

    private void assertIntColumn(DataSource dataSource, String name, String defaultValue) throws Exception {
        Map<String, Object> column = queryRow(dataSource, "SELECT COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT"
                + " FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_schedule'"
                + " AND COLUMN_NAME = '" + name + "'");
        assertNotEquals(0, column.size(), "t_schedule 必须有 " + name + " 列");
        assertEquals("int", column.get("column_type"));
        assertEquals("NO", column.get("is_nullable"));
        assertEquals(defaultValue, String.valueOf(column.get("column_default")));
    }

    private void assertMemberCardIndex(DataSource dataSource) throws Exception {
        assertEquals("member_id,card_type",
                String.valueOf(queryRow(dataSource,
                        "SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) AS index_columns"
                                + " FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE()"
                                + " AND TABLE_NAME = 't_member_card' AND INDEX_NAME = '" + MEMBER_CARD_INDEX + "'")
                                        .get("index_columns")),
                "t_member_card 按会员与卡类型建索引");
    }

    private void assertNoLevelPermission(DataSource dataSource) throws Exception {
        assertEquals(0, count(dataSource, "SELECT COUNT(*) FROM sys_menu WHERE menu_id = 2010"), "不保留独立等级按钮");
        assertEquals(0, count(dataSource, "SELECT COUNT(*) FROM sys_role_menu WHERE menu_id = 2010"), "不保留等级按钮角色绑定");
    }

    private DataSource dataSourceOf(MySQLContainer<?> container) {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");
        String url = container.getJdbcUrl();
        dataSource.setUrl(url + (url.contains("?") ? "&" : "?")
                + "useUnicode=true&characterEncoding=utf8&useSSL=false&allowPublicKeyRetrieval=true"
                + "&serverTimezone=Asia/Shanghai");
        dataSource.setUsername(container.getUsername());
        dataSource.setPassword(container.getPassword());
        return dataSource;
    }

    /** 逐条执行脚本；整行注释先行剔除，脚本内没有跨语句的存储过程 */
    private void executeScript(DataSource dataSource, Path script) throws IOException, SQLException {
        executeSql(dataSource, new String(Files.readAllBytes(script), StandardCharsets.UTF_8));
    }

    private void executeSql(DataSource dataSource, String content) throws SQLException {
        StringBuilder executable = new StringBuilder();
        for (String line : content.split("\\R")) {
            if (!line.trim().startsWith("--")) {
                executable.append(line).append('\n');
            }
        }
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            for (String sql : executable.toString().split(";")) {
                String trimmed = sql.trim();
                if (!trimmed.isEmpty()) {
                    statement.execute(trimmed);
                }
            }
        }
    }

    private void execute(DataSource dataSource, String sql) throws SQLException {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private long count(DataSource dataSource, String sql) throws SQLException {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql);
                ResultSet resultSet = statement.executeQuery()) {
            resultSet.next();
            return resultSet.getLong(1);
        }
    }

    private Map<String, Object> queryRow(DataSource dataSource, String sql) throws SQLException {
        Map<String, Object> row = new LinkedHashMap<String, Object>();
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql);
                ResultSet resultSet = statement.executeQuery()) {
            if (resultSet.next()) {
                for (int index = 1; index <= resultSet.getMetaData().getColumnCount(); index++) {
                    row.put(resultSet.getMetaData().getColumnLabel(index).toLowerCase(), resultSet.getObject(index));
                }
            }
        }
        return row;
    }
}
