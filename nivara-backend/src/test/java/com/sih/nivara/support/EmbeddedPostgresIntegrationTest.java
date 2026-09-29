package com.sih.nivara.support;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Base for integration tests that run the whole application over HTTP against a real PostgreSQL.
 *
 * <p>The database is an embedded PostgreSQL started once per test JVM and shared by every test
 * class, so Flyway applies the real migrations. Tests share it, so each test creates its own
 * accounts with unique emails ({@link #uniqueEmail}). The reminder scheduler is switched off, so
 * nothing changes data behind a test's back.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class EmbeddedPostgresIntegrationTest {

    private static final EmbeddedPostgres POSTGRES = start();

    private static EmbeddedPostgres start() {
        try {
            EmbeddedPostgres postgres = EmbeddedPostgres.builder().start();
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    postgres.close();
                } catch (IOException ignored) {
                    // the JVM is exiting anyway
                }
            }));
            return postgres;
        } catch (IOException e) {
            throw new IllegalStateException("Could not start embedded PostgreSQL", e);
        }
    }

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> POSTGRES.getJdbcUrl("postgres", "postgres"));
        registry.add("spring.datasource.username", () -> "postgres");
        registry.add("spring.datasource.password", () -> "postgres");
        registry.add("nivara.security.jwt.secret", () -> "integration-test-secret-integration-test-secret");
        registry.add("nivara.reminders.scheduler.enabled", () -> "false");
    }

    /** A response: its status, its body parsed as JSON (or null), and the raw response. */
    protected record Http(int status, JsonNode body, HttpResponse<String> raw) {
    }

    private final HttpClient client = HttpClient.newHttpClient();
    private final JsonMapper json = JsonMapper.builder().build();

    @Autowired
    private Environment environment;

    protected Http call(String method, String path, String token, String body) {
        try {
            HttpRequest.Builder request = HttpRequest.newBuilder(
                            URI.create("http://localhost:" + environment.getProperty("local.server.port") + path))
                    .method(method, body == null
                            ? HttpRequest.BodyPublishers.noBody()
                            : HttpRequest.BodyPublishers.ofString(body))
                    .header("Content-Type", "application/json");
            if (token != null) {
                request.header("Authorization", "Bearer " + token);
            }
            HttpResponse<String> response = client.send(request.build(), HttpResponse.BodyHandlers.ofString());
            String text = response.body() == null ? "" : response.body().trim();
            JsonNode node = text.startsWith("{") || text.startsWith("[") ? json.readTree(text) : null;
            return new Http(response.statusCode(), node, response);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    /** Calls and asserts the status, showing the body when it differs. */
    protected Http expect(int status, String method, String path, String token, String body) {
        Http response = call(method, path, token, body);
        assertEquals(status, response.status(), method + " " + path + " -> " + response.raw().body());
        return response;
    }

    protected static String uniqueEmail(String name) {
        return name + "-" + UUID.randomUUID().toString().substring(0, 8) + "@example.in";
    }

    protected static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asString();
    }

    /** Registers a caregiver and logs them in; answers the access token. */
    protected String caregiverToken(String fullName) {
        String email = uniqueEmail(fullName.toLowerCase().replace(' ', '.'));
        expect(201, "POST", "/api/auth/register", null,
                "{\"fullName\":\"" + fullName + "\",\"email\":\"" + email + "\",\"password\":\"Correct-Horse-9\"}");
        return text(expect(200, "POST", "/api/auth/login", null,
                "{\"email\":\"" + email + "\",\"password\":\"Correct-Horse-9\"}").body(), "accessToken");
    }

    /** Creates a patient as this caregiver, who becomes its OWNER; answers the patient uuid. */
    protected String createPatient(String caregiverToken, String fullName) {
        return text(expect(201, "POST", "/api/patients", caregiverToken,
                "{\"fullName\":\"" + fullName + "\"}").body(), "uuid");
    }
}
