package com.redis.monitoring.service;

import static com.redis.monitoring.model.Status.ERROR;
import static com.redis.monitoring.model.Status.SUCCESS;
import static com.redis.monitoring.model.Status.UNKNOWN;
import com.redis.monitoring.model.Status;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings("IllegalCatch")
public class RedisProbeService {

    @Value("${app.redis.cluster-name}")
    private String clusterName;

    @Value("${app.redis.probe-ttl}")
    private Duration probeTtl;

    @Value("${app.redis.metrics.min-bucket-bound}")
    private Duration minBucketBound;

    @Value("${app.redis.metrics.max-bucket-bound}")
    private Duration maxBucketBound;

    private final StringRedisTemplate redisTemplate;

    private final MeterRegistry meterRegistry;

    private final String instanceId = UUID.randomUUID().toString().substring(0, 8);

    @Scheduled(
        fixedDelayString = "${app.redis.probe-interval}",
        initialDelayString = "${app.redis.metrics.delay}"
    )
    public void performProbeCycle() {
        var probeKey = String.format("probe:%s:%s", clusterName, instanceId);
        log.debug("{} | Starting probe cycle for key: {}", clusterName, probeKey);

        if (measureStep("write", () -> performWriteProbe(probeKey)) != SUCCESS) {
            return;
        }
        measureStep("read", () -> performReadProbe(probeKey));
        measureStep("delete", () -> performDeleteProbe(probeKey));
    }

    private Status performWriteProbe(String key) {
        redisTemplate.opsForValue().set(key, key, probeTtl);
        log.debug("{} | Produced: {}", clusterName, key);
        return SUCCESS;
    }

    private Status performReadProbe(String key) {
        var actualValue = redisTemplate.opsForValue().get(key);

        if (actualValue == null) {
            log.warn("{} | Value missing for key: {}", clusterName, key);
            return ERROR;
        }
        if (!Objects.equals(key, actualValue)) {
            log.warn("{} | Value mismatch! Key: {}, Got: {}", clusterName, key, actualValue);
            return ERROR;
        }

        log.debug("{} | Consumed: {} (match check: OK)", clusterName, key);
        return SUCCESS;
    }

    private Status performDeleteProbe(String key) {
        var deleted = redisTemplate.delete(key);
        return deleted ? SUCCESS : ERROR;
    }

    private Status measureStep(String opType, Supplier<Status> action) {
        var status = UNKNOWN;
        var sample = Timer.start(meterRegistry);

        try {
            status = action.get();
        } catch (Exception e) {
            log.error("{} | Redis E2E {} failed: {}", clusterName, opType, e.getMessage(), e);
            status = ERROR;
        } finally {
            recordMetrics(opType, status, sample);
        }

        return status;
    }

    private void recordMetrics(String opType, Status status, Timer.Sample sample) {
        var timer = Timer.builder("redis_e2e_" + opType + "_milliseconds")
            .tag("status", status.name())
            .minimumExpectedValue(minBucketBound)
            .maximumExpectedValue(maxBucketBound)
            .register(meterRegistry);

        sample.stop(timer);
    }

}