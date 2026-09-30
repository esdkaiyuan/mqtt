package com.mqtt.cloud.service;

import tools.jackson.databind.ObjectMapper;
import com.mqtt.cloud.config.EmqxProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmqxClientKickerTest {

    private static final String BASE_URL = "http://emqx:18083/api/v5";

    @Mock
    private RestTemplate restTemplate;

    private EmqxProperties properties;

    private EmqxClientKicker kicker;

    @BeforeEach
    void setUp() {
        properties = new EmqxProperties();
        properties.setApiBaseUrl(BASE_URL);
        properties.setDashboardUser("admin");
        properties.setDashboardPassword("secret");
        kicker = new EmqxClientKicker(restTemplate, properties, new ObjectMapper());
    }

    @Test
    void kick_should_skip_http_when_disabled() {
        properties.setKickEnabled(false);

        assertThat(kicker.kickByUsername("esp32-fall.sensor-01")).isZero();

        verifyNoInteractions(restTemplate);
    }

    @Test
    void kick_should_skip_http_when_username_blank() {
        assertThat(kicker.kickByUsername("  ")).isZero();

        verifyNoInteractions(restTemplate);
    }

    @Test
    void kick_should_login_list_and_delete_each_online_client() {
        stubLogin("jwt-token");
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), eq(String.class)))
                .thenReturn(ResponseEntity.ok("{\"data\":[{\"clientid\":\"c-1\"},{\"clientid\":\"c-2\"}]}"));
        when(restTemplate.exchange(anyString(), eq(HttpMethod.DELETE), any(), eq(String.class)))
                .thenReturn(ResponseEntity.<String>noContent().build());

        assertThat(kicker.kickByUsername("esp32-fall.sensor-01")).isEqualTo(2);

        ArgumentCaptor<String> urls = ArgumentCaptor.forClass(String.class);
        verify(restTemplate, times(4)).exchange(urls.capture(), any(), any(), eq(String.class));
        assertThat(urls.getAllValues()).containsExactly(
                BASE_URL + "/login",
                BASE_URL + "/clients?limit=100&username=esp32-fall.sensor-01",
                BASE_URL + "/clients/c-1",
                BASE_URL + "/clients/c-2");
    }

    @Test
    void kick_should_reuse_cached_token_across_calls() {
        stubLogin("jwt-token");
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), eq(String.class)))
                .thenReturn(ResponseEntity.ok("{\"data\":[]}"));

        kicker.kickByUsername("p.d");
        kicker.kickByUsername("p.d");

        verify(restTemplate, times(1)).exchange(anyString(), eq(HttpMethod.POST), any(), eq(String.class));
    }

    @Test
    void kick_should_count_only_successful_deletes() {
        stubLogin("jwt-token");
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), eq(String.class)))
                .thenReturn(ResponseEntity.ok("{\"data\":[{\"clientid\":\"c-1\"},{\"clientid\":\"c-2\"}]}"));
        when(restTemplate.exchange(anyString(), eq(HttpMethod.DELETE), any(), eq(String.class)))
                .thenReturn(ResponseEntity.<String>noContent().build())
                .thenThrow(new RestClientException("client already gone"));

        assertThat(kicker.kickByUsername("p.d")).isEqualTo(1);
    }

    @Test
    void kick_should_swallow_login_failure() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(), eq(String.class)))
                .thenThrow(new RestClientException("emqx unreachable"));

        assertThat(kicker.kickByUsername("p.d")).isZero();
    }

    @Test
    void kick_should_return_zero_when_login_response_has_no_token() {
        stubLogin(null);

        assertThat(kicker.kickByUsername("p.d")).isZero();

        verify(restTemplate, times(1)).exchange(anyString(), eq(HttpMethod.POST), any(), eq(String.class));
    }

    private void stubLogin(String token) {
        String body = token == null ? "{\"version\":\"5.0.26\"}" : "{\"token\":\"" + token + "\"}";
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(), eq(String.class)))
                .thenReturn(ResponseEntity.ok(body));
    }
}