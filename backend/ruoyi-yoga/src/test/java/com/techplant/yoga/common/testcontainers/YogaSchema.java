package com.techplant.yoga.common.testcontainers;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.sql.DataSource;

/** 用 backend/sql 下的脚本里的建表语句重建集成测试用表。 */
public final class YogaSchema
{
    /** 业务库脚本：业务表与业务菜单、字典 */
    private static final String BUSINESS_SCRIPT = "yoga.sql";

    /** 若依基础库脚本：sys_* 基础表（业务脚本里没有这些建表语句） */
    private static final String BASE_SCRIPT = "ry_20260417.sql";

    private YogaSchema()
    {
    }

    /**
     * 先删除再按脚本重建给定表；只建表不导数据，测试数据由各测试自行准备。
     *
     * <p>建表语句优先取业务脚本，业务脚本里没有的表（如 {@code sys_dict_data}）回退到基础库脚本。</p>
     */
    public static void reset(DataSource dataSource, String... tables)
    {
        Path businessScript = locateScript(BUSINESS_SCRIPT);
        String businessContent = read(businessScript);
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement())
        {
            for (String table : tables)
            {
                statement.execute("DROP TABLE IF EXISTS `" + table + "`");
                statement.execute(createTableStatement(businessContent, businessScript, table));
            }
        }
        catch (SQLException e)
        {
            throw new IllegalStateException("重建测试表失败：" + String.join(", ", tables), e);
        }
    }

    private static String createTableStatement(String businessContent, Path businessScript, String table)
    {
        String statement = findCreateTable(businessContent, table);
        if (statement != null)
        {
            return statement;
        }
        Path baseScript = locateScript(BASE_SCRIPT);
        statement = findCreateTable(read(baseScript), table);
        if (statement == null)
        {
            throw new IllegalStateException(businessScript + " 与 " + baseScript + " 中都没有 " + table + " 的建表语句");
        }
        return statement;
    }

    /**
     * 匹配 {@code CREATE TABLE} 语句，兼容带反引号与不带反引号的两种写法
     */
    private static String findCreateTable(String scriptContent, String table)
    {
        Matcher matcher = Pattern.compile("(?is)CREATE\\s+TABLE\\s+`?" + table + "`?\\s*\\(.*?;").matcher(scriptContent);
        return matcher.find() ? matcher.group() : null;
    }

    private static String read(Path script)
    {
        try
        {
            return new String(Files.readAllBytes(script), StandardCharsets.UTF_8);
        }
        catch (IOException e)
        {
            throw new IllegalStateException("读取 " + script + " 失败", e);
        }
    }

    /**
     * 从工作目录逐级向上找 {@code backend/sql/<脚本名>}，兼容在仓库根目录、backend 目录或模块目录下运行测试；
     * 候选位置必须与同级 pom.xml 同时存在，避免命中其它目录的同名脚本。
     */
    private static Path locateScript(String scriptName)
    {
        Path directory = Paths.get("").toAbsolutePath();
        while (directory != null)
        {
            Path moduleCandidate = directory.resolve("backend/sql/" + scriptName);
            if (Files.isRegularFile(moduleCandidate) && Files.isRegularFile(directory.resolve("backend/pom.xml")))
            {
                return moduleCandidate;
            }
            Path backendCandidate = directory.resolve("sql/" + scriptName);
            if (Files.isRegularFile(backendCandidate) && Files.isRegularFile(directory.resolve("pom.xml")))
            {
                return backendCandidate;
            }
            directory = directory.getParent();
        }
        throw new IllegalStateException("找不到 " + scriptName + "，工作目录＝" + Paths.get("").toAbsolutePath());
    }
}
