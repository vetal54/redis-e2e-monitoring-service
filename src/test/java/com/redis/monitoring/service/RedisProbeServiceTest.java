package com.redis.monitoring.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class RedisProbeServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private SimpleMeterRegistry meterRegistry;

    private RedisProbeService service;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        service = new RedisProbeService(redisTemplate, meterRegistry);

        ReflectionTestUtils.setField(service, "clusterName", "test-cluster");
        ReflectionTestUtils.setField(service, "probeTtl", Duration.ofSeconds(10));
        ReflectionTestUtils.setField(service, "minBucketBound", Duration.ofMillis(1));
        ReflectionTestUtils.setField(service, "maxBucketBound", Duration.ofMillis(10));
    }

    @Test
    void shouldPerformSuccessfulCycle() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        when(redisTemplate.delete(anyString())).thenReturn(true);

        service.performProbeCycle();

        verify(valueOperations).set(anyString(), anyString(), any(Duration.class));
        verify(valueOperations).get(anyString());
        verify(redisTemplate).delete(anyString());

        assertMetric("redis_e2e_write_milliseconds", "SUCCESS");
        assertMetric("redis_e2e_read_milliseconds", "SUCCESS");
        assertMetric("redis_e2e_delete_milliseconds", "SUCCESS");
    }

    @Test
    void shouldHandleWriteError() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        doThrow(new RedisConnectionFailureException("Redis connection fail"))
            .when(valueOperations).set(anyString(), anyString(), any(Duration.class));

        service.performProbeCycle();

        verify(valueOperations, never()).get(anyString());

        assertMetric("redis_e2e_write_milliseconds", "ERROR");
    }

    private void assertMetric(String name, String status) {
        var summary = meterRegistry.find(name).tag("status", status).timer();
        assertThat(summary).isNotNull();
        assertThat(summary.count()).isGreaterThan(0);
    }

}
