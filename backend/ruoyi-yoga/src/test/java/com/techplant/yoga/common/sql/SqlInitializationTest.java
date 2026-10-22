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
            YogaSchema.reset(dataSource, "sys_menu", "sys_role_menu", "sys_dict_type", "sys_dict_data");
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
        } finally {
            container.stop();
        }
    }

    private void assertInitialized(DataSource dataSource) throws Exception {
        assertLevelColumn(dataSource);
        assertEquals(3, count(dataSource, "SELECT COUNT(*) FROM t_coach"), "样例教练 3 条");
        assertEquals(0, count(dataSource, "SELECT COUNT(*) FROM t_coach WHERE level <> 1"), "样例教练均为普通等级");
        assertNoLevelPermission(dataSource);
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
