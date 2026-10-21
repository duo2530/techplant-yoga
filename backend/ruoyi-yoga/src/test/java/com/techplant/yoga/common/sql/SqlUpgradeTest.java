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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;
import com.techplant.yoga.common.testcontainers.YogaSchema;

/**
 * 数据库脚本验证：在一次性隔离 MySQL 容器里验证新建库导入与已有库增量升级。
 *
 * <p>不连接开发库；容器在本用例内启动并回收。升级场景通过“新库去掉 level 列与新增菜单”模拟旧库，
 * 不维护旧版建表脚本副本。</p>
 */
@DisplayName("SQL 脚本（隔离 MySQL：新建库导入 / 旧库升级 / 幂等 / 数据保留）")
class SqlUpgradeTest {
    private static final String IMAGE = "mysql:8.0";

    private static final String DATABASE = "techplant_yoga_upgrade";

    private static final String BUSINESS_SCRIPT = "yoga.sql";

    private static final String UPGRADE_SCRIPT = "yoga_upgrade_v2.6_gold_coach.sql";

    private static final long SAMPLE_COACH_ID = 1856739201475237002L;

    private static final long OLD_COACH_ID = 9101L;

    @Test
    @DisplayName("新建库导入 yoga.sql 正常；旧库升级后数据保留、可重复执行且不覆盖运营角色授权")
    void freshImportAndExistingDatabaseUpgrade() throws Exception {
        MySQLContainer<?> container = new MySQLContainer<>(DockerImageName.parse(IMAGE)).withDatabaseName(DATABASE)
                .withUsername("yoga").withPassword("yoga").withCommand("--default-time-zone=+08:00");
        container.start();
        try {
            DataSource dataSource = dataSourceOf(container);
            // 基础库的菜单与字典表由若依基础脚本建；其余业务表与数据由 yoga.sql 建
            YogaSchema.reset(dataSource, "sys_menu", "sys_role_menu", "sys_dict_type", "sys_dict_data");
            executeScript(dataSource, YogaSchema.locateScript(BUSINESS_SCRIPT));

            assertLevelColumn(dataSource);
            assertEquals(3, count(dataSource, "SELECT COUNT(*) FROM t_coach"), "样例教练 3 条");
            assertEquals(0, count(dataSource, "SELECT COUNT(*) FROM t_coach WHERE level <> 1"), "新建库样例教练一律普通等级");
            assertMenuPermission(dataSource);
            assertEquals(9, count(dataSource, "SELECT COUNT(*) FROM sys_menu WHERE menu_id BETWEEN 2000 AND 2008"),
                    "既有业务菜单 2000~2008 完整");

            // 记录升级前的旧数据与关联
            Map<String, Object> sampleCoach = queryRow(dataSource,
                    "SELECT name, intro, gallery FROM t_coach WHERE id = " + SAMPLE_COACH_ID);
            long oldCoachCount = count(dataSource, "SELECT COUNT(*) FROM t_coach");
            long scheduleCount = count(dataSource, "SELECT COUNT(*) FROM t_schedule");
            long sampleCoachSchedules = count(dataSource,
                    "SELECT COUNT(*) FROM t_schedule WHERE coach_id = " + SAMPLE_COACH_ID);
            assertTrue(sampleCoachSchedules > 0, "样例排课关联样例教练");
            List<Long> existingMenus = queryLongList(dataSource,
                    "SELECT menu_id FROM sys_menu WHERE menu_id BETWEEN 2000 AND 2008 ORDER BY menu_id");
            List<Long> existingRoleMenus = queryLongList(dataSource,
                    "SELECT menu_id FROM sys_role_menu WHERE menu_id BETWEEN 2000 AND 2008 ORDER BY menu_id");

            simulateOldDatabase(dataSource);
            assertEquals(existingMenus,
                    queryLongList(dataSource,
                            "SELECT menu_id FROM sys_menu WHERE menu_id BETWEEN 2000 AND 2008 ORDER BY menu_id"),
                    "模拟旧库不改动既有菜单");
            assertEquals(0, count(dataSource, "SELECT COUNT(*) FROM sys_menu WHERE menu_id = 2010"), "旧库没有 2010 按钮");

            executeScript(dataSource, YogaSchema.locateScript(UPGRADE_SCRIPT));
            assertMenuPermission(dataSource);
            Map<String, Object> menuAfterFirstUpgrade = queryRow(dataSource,
                    "SELECT menu_name, parent_id, order_num, path, menu_type, perms, status, remark"
                            + " FROM sys_menu WHERE menu_id = 2010");

            // 首次升级后管理员把「金牌教练设置」授予运营角色（role_id = 2）
            execute(dataSource, "INSERT INTO sys_role_menu (role_id, menu_id) VALUES (2, 2010)");

            // 第二遍验证幂等：不报错、不重复写菜单、保留既有角色授权
            executeScript(dataSource, YogaSchema.locateScript(UPGRADE_SCRIPT));

            assertLevelColumn(dataSource);
            assertEquals(oldCoachCount + 1, count(dataSource, "SELECT COUNT(*) FROM t_coach"), "旧数据不丢失");
            assertEquals(0, count(dataSource, "SELECT COUNT(*) FROM t_coach WHERE level <> 1"), "升级后全部旧教练为普通等级");
            assertEquals(Integer.valueOf(1),
                    queryInt(dataSource, "SELECT level FROM t_coach WHERE id = " + OLD_COACH_ID), "新加列默认值为 1");
            assertEquals(sampleCoach,
                    queryRow(dataSource, "SELECT name, intro, gallery FROM t_coach WHERE id = " + SAMPLE_COACH_ID),
                    "旧资料保持不变");
            assertEquals(scheduleCount + 1, count(dataSource, "SELECT COUNT(*) FROM t_schedule"), "排课不丢失");
            assertEquals(sampleCoachSchedules,
                    count(dataSource, "SELECT COUNT(*) FROM t_schedule WHERE coach_id = " + SAMPLE_COACH_ID),
                    "旧排课关联保留");
            assertEquals(1, count(dataSource, "SELECT COUNT(*) FROM t_schedule WHERE coach_id = " + OLD_COACH_ID),
                    "旧教练的排课关联保留");
            assertMenuPermission(dataSource);
            assertEquals(menuAfterFirstUpgrade,
                    queryRow(dataSource,
                            "SELECT menu_name, parent_id, order_num, path, menu_type, perms, status, remark"
                                    + " FROM sys_menu WHERE menu_id = 2010"),
                    "重复执行菜单行内容不变");
            assertEquals(existingMenus,
                    queryLongList(dataSource,
                            "SELECT menu_id FROM sys_menu WHERE menu_id BETWEEN 2000 AND 2008 ORDER BY menu_id"),
                    "升级后既有菜单完整保留");
            assertEquals(existingRoleMenus,
                    queryLongList(dataSource,
                            "SELECT menu_id FROM sys_role_menu WHERE menu_id BETWEEN 2000 AND 2008 ORDER BY menu_id"),
                    "升级后既有角色菜单绑定完整保留");
            assertEquals(1, count(dataSource, "SELECT COUNT(*) FROM sys_menu WHERE menu_id = 2010"), "2010 只保留一条菜单行");
            assertEquals(1,
                    count(dataSource, "SELECT COUNT(*) FROM sys_role_menu WHERE role_id = 1 AND menu_id = 2010"),
                    "管理员角色绑定不重复");
            assertEquals(1,
                    count(dataSource, "SELECT COUNT(*) FROM sys_role_menu WHERE role_id = 2 AND menu_id = 2010"),
                    "重复执行保留运营角色已授予的绑定");
            assertEquals(2, count(dataSource, "SELECT COUNT(*) FROM sys_role_menu WHERE menu_id = 2010"),
                    "2010 只有管理员与运营角色两条绑定");
            assertEquals(existingMenus.size() + 1,
                    count(dataSource, "SELECT COUNT(*) FROM sys_menu WHERE menu_id BETWEEN 2000 AND 2010"),
                    "业务菜单总数只增加 2010");
        } finally {
            container.stop();
        }
    }

    /**
     * 把新建库改造成旧版形态：只去掉 level 列与新增的 2010 菜单/绑定，既有菜单与旧数据保持原样。
     */
    private void simulateOldDatabase(DataSource dataSource) throws Exception {
        execute(dataSource, "ALTER TABLE t_coach DROP COLUMN level");
        execute(dataSource, "DELETE FROM sys_role_menu WHERE menu_id = 2010");
        execute(dataSource, "DELETE FROM sys_menu WHERE menu_id = 2010");
        execute(dataSource,
                "INSERT INTO t_coach (id, name, phone) VALUES (" + OLD_COACH_ID + ", '旧教练'," + " '13800000009')");
        execute(dataSource,
                "INSERT INTO t_schedule (id, store_id, course_id, course_type, coach_id, classroom_id,"
                        + " schedule_date, start_time, end_time, max_persons, min_persons, booked_persons, status)"
                        + " VALUES (9201, 1856739201475235901, 1856739201475235801, 1, " + OLD_COACH_ID + ","
                        + " 1856739201475236001, CURDATE(), CONCAT(CURDATE(), ' 10:00:00'),"
                        + " CONCAT(CURDATE(), ' 11:00:00'), 10, 2, 0, 2)");
        assertEquals(0,
                count(dataSource, "SELECT COUNT(*) FROM information_schema.COLUMNS"
                        + " WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_coach' AND COLUMN_NAME = 'level'"),
                "旧库没有 level 列");
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

    private void assertMenuPermission(DataSource dataSource) throws Exception {
        Map<String, Object> menu = queryRow(dataSource,
                "SELECT menu_name, parent_id, menu_type, perms, status" + " FROM sys_menu WHERE menu_id = 2010");
        assertEquals("金牌教练设置", menu.get("menu_name"));
        assertEquals(2003L, ((Number) menu.get("parent_id")).longValue(), "按钮挂在教练管理下");
        assertEquals("F", menu.get("menu_type"));
        assertEquals("yoga:coach:level", menu.get("perms"));
        assertEquals("0", menu.get("status"));
        assertEquals(1, count(dataSource, "SELECT COUNT(*) FROM sys_role_menu WHERE role_id = 1 AND menu_id = 2010"),
                "内置管理员角色拥有该按钮权限");
    }

    // ------------------------------------------------------------------
    // 脚本执行与查询
    // ------------------------------------------------------------------

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
        String content = new String(Files.readAllBytes(script), StandardCharsets.UTF_8);
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

    private Integer queryInt(DataSource dataSource, String sql) throws SQLException {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql);
                ResultSet resultSet = statement.executeQuery()) {
            return resultSet.next() ? resultSet.getInt(1) : null;
        }
    }

    private List<Long> queryLongList(DataSource dataSource, String sql) throws SQLException {
        List<Long> values = new ArrayList<Long>();
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql);
                ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                values.add(resultSet.getLong(1));
            }
        }
        return values;
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
