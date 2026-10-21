package com.techplant.yoga.common.testcontainers;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 集成测试共用的 Redis 容器：一个 JVM 内只启动一个实例，由 Testcontainers 的 Ryuk 在进程退出后回收。
 */
public final class YogaRedisContainer {
    private static final String IMAGE = "redis:7";

    private static final int REDIS_PORT = 6379;

    private static final GenericContainer<?> CONTAINER = new GenericContainer<>(DockerImageName.parse(IMAGE))
            .withExposedPorts(REDIS_PORT);

    static {
        CONTAINER.start();
    }

    private YogaRedisContainer() {
    }

    public static String host() {
        return CONTAINER.getHost();
    }

    public static int port() {
        return CONTAINER.getMappedPort(REDIS_PORT);
    }
}
