package com.techplant.yoga.common.testcontainers;

import java.time.Instant;
import java.time.ZoneId;
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;

/**
 * 集成测试共用的 MySQL 容器：一个 JVM 内只启动一个实例，由 Testcontainers 的 Ryuk 在进程退出后回收。
 */
public final class YogaMySqlContainer {
    private static final String IMAGE = "mysql:8.0";

    private static final String DATABASE = "techplant_yoga_test";

    private static final String USERNAME = "yoga";

    private static final String PASSWORD = "yoga";

    private static final String TIME_ZONE_OFFSET = currentOffset();

    private static final String URL_PARAMETERS = "?useUnicode=true&characterEncoding=utf8"
            + "&zeroDateTimeBehavior=convertToNull&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone="
            + TIME_ZONE_OFFSET.replace("+", "%2B");

    private static final MySQLContainer<?> CONTAINER = new MySQLContainer<>(IMAGE).withDatabaseName(DATABASE)
            .withUsername(USERNAME).withPassword(PASSWORD).withEnv("TZ", ZoneId.systemDefault().getId());

    static {
        CONTAINER.start();
    }

    private YogaMySqlContainer() {
    }

    public static DataSource dataSource() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");
        dataSource.setUrl("jdbc:mysql://" + CONTAINER.getHost() + ":"
                + CONTAINER.getMappedPort(MySQLContainer.MYSQL_PORT) + "/" + DATABASE + URL_PARAMETERS);
        dataSource.setUsername(USERNAME);
        dataSource.setPassword(PASSWORD);
        return dataSource;
    }

    private static String currentOffset() {
        int seconds = ZoneId.systemDefault().getRules().getOffset(Instant.now()).getTotalSeconds();
        return String.format("%s%02d:%02d", seconds < 0 ? "-" : "+", Math.abs(seconds) / 3600,
                Math.abs(seconds) % 3600 / 60);
    }
}
