package com.redis.monitoring.integration;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.testcontainers.shaded.org.awaitility.Awaitility;

@Tag("integration")
public class RedisE2eIntegrationTest extends AbstractRedisIntegrationTest {

    @Autowired
    private MeterRegistry meterRegistry;

    @Test
    void shouldCompleteRedisE2eFlow() {
        Awaitility.await()
            .atMost(Duration.ofSeconds(30))
            .pollInterval(Duration.ofSeconds(1))
            .untilAsserted(() -> {
                var write = meterRegistry.find("redis_e2e_write_milliseconds").timer();
                assertNotNull(write, "Write metric missing");
                assertTrue(write.count() > 0, "Write count should be > 0");

                var read = meterRegistry.find("redis_e2e_read_milliseconds").timer();
                assertNotNull(read, "Read metric missing");
                assertTrue(read.count() > 0, "Read count should be > 0");

                var delete = meterRegistry.find("redis_e2e_delete_milliseconds").timer();
                assertNotNull(delete, "Delete metric missing");
                assertTrue(delete.count() > 0, "Delete count should be > 0");
            });
    }

}
