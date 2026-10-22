package com.mqtt.cloud.controller;

import com.mqtt.cloud.entity.IngestDeadLetter;
import com.mqtt.cloud.service.IngestDeadLetterService;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HealthControllerTest {

    private JdbcTemplate jdbcTemplate;
    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private MeterRegistry meterRegistry;
    private IngestDeadLetterService deadLetterService;
    private HealthController controller;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        meterRegistry = mock(MeterRegistry.class);
        deadLetterService = mock(IngestDeadLetterService.class);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(meterRegistry.getMeters()).thenReturn(List.of());
        when(deadLetterService.countByStatus())
                .thenReturn(Map.of(IngestDeadLetter.STATUS_PENDING, 0L));

        controller = new HealthController(jdbcTemplate, redisTemplate, meterRegistry, deadLetterService);
    }

    @Test
    void readiness_should_return_200_when_dependencies_are_up() {
        when(jdbcTemplate.queryForObject("SELECT 1", Integer.class)).thenReturn(1);

        ResponseEntity<Map<String, Object>> response = controller.ready();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("status", "UP");
    }

    @Test
    void readiness_should_return_503_when_database_is_down() {
        when(jdbcTemplate.queryForObject("SELECT 1", Integer.class))
                .thenThrow(new IllegalStateException("db down"));

        ResponseEntity<Map<String, Object>> response = controller.ready();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).containsEntry("status", "DOWN");
    }

    @Test
    void readiness_should_return_503_when_dead_letters_are_backlogged() {
        when(jdbcTemplate.queryForObject("SELECT 1", Integer.class)).thenReturn(1);
        when(deadLetterService.countByStatus())
                .thenReturn(Map.of(IngestDeadLetter.STATUS_PENDING, 1001L));

        ResponseEntity<Map<String, Object>> response = controller.ready();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).containsEntry("status", "DOWN");
    }
}
