package com.redis.monitoring.util;

import lombok.experimental.UtilityClass;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

@UtilityClass
public class RedisTestContainer {

    private static final GenericContainer<?> REDIS_CONTAINER = new GenericContainer<>(
        DockerImageName.parse("redis:alpine")).withExposedPorts(6379);

    public static GenericContainer<?> getInstance() {
        if (!REDIS_CONTAINER.isRunning()) {
            REDIS_CONTAINER.start();
        }
        return REDIS_CONTAINER;
    }

}
