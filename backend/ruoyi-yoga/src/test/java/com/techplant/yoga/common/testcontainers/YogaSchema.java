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

/** 用 backend/sql/yoga.sql 里的建表语句重建集成测试用表。 */
public final class YogaSchema
{
    private YogaSchema()
    {
    }

    /**
     * 先删除再按 yoga.sql 重建给定表；只建表不导数据，测试数据由各测试自行准备。
     */
    public static void reset(DataSource dataSource, String... tables)
    {
        Path script = locateScript();
        String content = read(script);
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement())
        {
            for (String table : tables)
            {
                statement.execute("DROP TABLE IF EXISTS `" + table + "`");
                statement.execute(createTableStatement(content, script, table));
            }
        }
        catch (SQLException e)
        {
            throw new IllegalStateException("重建测试表失败：" + String.join(", ", tables), e);
        }
    }

    private static String createTableStatement(String scriptContent, Path scriptPath, String table)
    {
        Matcher matcher = Pattern.compile("(?is)CREATE\\s+TABLE\\s+`" + table + "`.*?;").matcher(scriptContent);
        if (!matcher.find())
        {
            throw new IllegalStateException(scriptPath + " 中没有 " + table + " 的建表语句");
        }
        return matcher.group();
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
     * 从工作目录逐级向上找 yoga.sql，兼容在仓库根目录、backend 目录或模块目录下运行测试；
     * 候选位置必须与同级 pom.xml 同时存在，避免命中其它目录的同名脚本。
     */
    private static Path locateScript()
    {
        Path directory = Paths.get("").toAbsolutePath();
        while (directory != null)
        {
            Path moduleCandidate = directory.resolve("backend/sql/yoga.sql");
            if (Files.isRegularFile(moduleCandidate) && Files.isRegularFile(directory.resolve("backend/pom.xml")))
            {
                return moduleCandidate;
            }
            Path backendCandidate = directory.resolve("sql/yoga.sql");
            if (Files.isRegularFile(backendCandidate) && Files.isRegularFile(directory.resolve("pom.xml")))
            {
                return backendCandidate;
            }
            directory = directory.getParent();
        }
        throw new IllegalStateException("找不到 backend/sql/yoga.sql，工作目录＝" + Paths.get("").toAbsolutePath());
    }
}
