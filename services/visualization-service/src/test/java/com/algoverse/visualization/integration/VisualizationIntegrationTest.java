package com.algoverse.visualization.integration;

import com.algoverse.visualization.domain.TraceRequest;
import com.algoverse.visualization.domain.TraceResponse;
import com.redis.testcontainers.RedisContainer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
@DisplayName("Visualization Service — Integration")
class VisualizationIntegrationTest {

    @Container
    static final RedisContainer REDIS =
            new RedisContainer(DockerImageName.parse("redis:7-alpine"));

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("TEST_REDIS_HOST", REDIS::getHost);
        registry.add("TEST_REDIS_PORT", () -> REDIS.getMappedPort(6379));
    }

    @LocalServerPort int port;
    @Autowired TestRestTemplate rest;

    private String traceUrl() {
        return "http://localhost:" + port + "/api/v1/visualize/trace";
    }

    // ── Validation ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /trace — input validation")
    class Validation {

        @Test
        @DisplayName("returns 400 when language is blank")
        void blankLanguage_400() {
            TraceRequest req = new TraceRequest("", "x = 1", null);
            ResponseEntity<String> resp = rest.postForEntity(traceUrl(), req, String.class);
            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("returns 400 when language is unsupported")
        void unsupportedLanguage_400() {
            TraceRequest req = new TraceRequest("ruby", "puts 1", null);
            ResponseEntity<String> resp = rest.postForEntity(traceUrl(), req, String.class);
            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("returns 400 when code is blank")
        void blankCode_400() {
            TraceRequest req = new TraceRequest("python", "", null);
            ResponseEntity<String> resp = rest.postForEntity(traceUrl(), req, String.class);
            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("returns 400 when code exceeds 32 KB")
        void oversizedCode_400() {
            String bigCode = "x = 1\n".repeat(6_000);  // ~42 KB
            TraceRequest req = new TraceRequest("python", bigCode, null);
            ResponseEntity<String> resp = rest.postForEntity(traceUrl(), req, String.class);
            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    // ── Python tracing ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /trace — Python execution")
    class PythonTrace {

        @Test
        @DisplayName("returns 200 with non-empty steps for valid Python assignment")
        void pythonAssignment_hasSteps() {
            TraceRequest req = new TraceRequest("python", "x = 42\nprint(x)", null);
            ResponseEntity<TraceResponse> resp = rest.postForEntity(traceUrl(), req, TraceResponse.class);

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(resp.getBody()).isNotNull();
            assertThat(resp.getBody().steps()).isNotEmpty();
        }

        @Test
        @DisplayName("returns trace with stdout captured in final step")
        void pythonPrint_capturesOutput() {
            TraceRequest req = new TraceRequest("python", "print('hello')", null);
            ResponseEntity<TraceResponse> resp = rest.postForEntity(traceUrl(), req, TraceResponse.class);

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
            TraceResponse body = resp.getBody();
            assertThat(body).isNotNull();
            // At least one step should capture stdout
            boolean hasOutput = body.steps().stream()
                    .anyMatch(step -> step.stdout() != null && step.stdout().contains("hello"));
            assertThat(hasOutput).isTrue();
        }

        @Test
        @DisplayName("null input defaults to empty string without error")
        void pythonNullInput_noError() {
            TraceRequest req = new TraceRequest("python", "x = 1", null);
            ResponseEntity<TraceResponse> resp = rest.postForEntity(traceUrl(), req, TraceResponse.class);
            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
    }

    // ── JavaScript tracing ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /trace — JavaScript execution")
    class JavaScriptTrace {

        @Test
        @DisplayName("returns 200 with steps for a basic JS snippet")
        void jsAssignment_hasSteps() {
            TraceRequest req = new TraceRequest("javascript", "let x = 10;\nconsole.log(x);", null);
            ResponseEntity<TraceResponse> resp = rest.postForEntity(traceUrl(), req, TraceResponse.class);

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(resp.getBody()).isNotNull();
            assertThat(resp.getBody().steps()).isNotEmpty();
        }
    }

    // ── Caching ────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Cache behaviour")
    class Caching {

        @Test
        @DisplayName("second identical request returns same result (cache hit)")
        void cacheHit_sameResult() {
            TraceRequest req = new TraceRequest("python", "y = 99", null);

            ResponseEntity<TraceResponse> first  = rest.postForEntity(traceUrl(), req, TraceResponse.class);
            ResponseEntity<TraceResponse> second = rest.postForEntity(traceUrl(), req, TraceResponse.class);

            assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);

            // Both responses must contain equivalent step counts
            assertThat(first.getBody().steps().size())
                    .isEqualTo(second.getBody().steps().size());
        }
    }
}
