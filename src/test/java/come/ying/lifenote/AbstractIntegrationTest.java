package come.ying.lifenote;

import come.ying.lifenote.service.LlmChatService;
import come.ying.lifenote.service.ReminderEmailService;
import come.ying.lifenote.service.S3StorageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base for every integration test.
 *
 * The PostgreSQL container is a Spring bean (declared in {@link Containers}),
 * so its lifetime follows the shared application context instead of a single
 * test class: all integration tests reuse one context, one container and one
 * database, and the container is only stopped when the JVM exits. The previous
 * per-class JUnit lifecycle stopped the container after the first test class
 * while the context (and its connection pool) stayed cached - every later
 * class then failed with "connection refused".
 *
 * The "test" profile swaps real credentials for dummies, scheduling is off
 * (tests call the reminder jobs directly), and MockMvc drives HTTP calls
 * without a real server.
 *
 * The three external systems are replaced by Mockito fakes - a "fake LLM"
 * as the proposal's evaluation plan requires, plus S3 and the mail sender -
 * so no test ever reaches a real API. Each test method registers its own
 * user, which keeps the shared database free of cross-test interference.
 */
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(AbstractIntegrationTest.Containers.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
public abstract class AbstractIntegrationTest {

    @TestConfiguration
    static class Containers {

        @Bean
        @ServiceConnection
        PostgreSQLContainer<?> postgres() {
            return new PostgreSQLContainer<>("postgres:16");
        }
    }

    @Autowired
    protected MockMvc mockMvc;

    /** Fake LLM: tests stub {@code chat(...)} instead of calling a provider. */
    @MockitoBean
    protected LlmChatService llmChatService;

    /** Fake object storage: uploads, presigned URLs and wipes are recorded, never performed. */
    @MockitoBean
    protected S3StorageService s3StorageService;

    /** Fake mail sender: reminder emails are "best effort", tests only verify they were offered. */
    @MockitoBean
    protected ReminderEmailService reminderEmailService;

    // ---------- user + token helpers ----------

    protected static final String PASSWORD = "Passw0rd!";

    protected record TestUser(String email, String token) {}

    protected static final ObjectMapper JSON = new ObjectMapper();

    /** Registers a fresh user and logs them in; every test gets its own account. */
    protected TestUser createUser() throws Exception {
        String email = "user-" + UUID.randomUUID() + "@test.com";
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"username\":\"tester\","
                                + "\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        return new TestUser(email, login(email, PASSWORD));
    }

    protected String login(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andReturn();
        return data(result).get("token").toString();
    }

    protected org.springframework.test.web.servlet.ResultActions auth(
            TestUser user,
            org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder<?> req)
            throws Exception {
        return mockMvc.perform(req.header("Authorization", "Bearer " + user.token()));
    }

    // ---------- response helpers ----------

    /** Parses the whole {"code","msg","data"} envelope into a map. */
    @SuppressWarnings("unchecked")
    protected Map<String, Object> envelope(MvcResult result) {
        return JSON.readValue(
                new String(result.getResponse().getContentAsByteArray(), java.nio.charset.StandardCharsets.UTF_8),
                Map.class);
    }

    /** The data payload as-is: object or list depending on the endpoint. */
    protected Object payload(MvcResult result) {
        return envelope(result).get("data");
    }

    /** Parses the data payload of an object-shaped response. */
    @SuppressWarnings("unchecked")
    protected Map<String, Object> data(MvcResult result) {
        return (Map<String, Object>) payload(result);
    }

    /** Parses the data payload of a list-shaped response. */
    protected List<?> dataList(MvcResult result) {
        return (List<?>) payload(result);
    }

    protected void assertCode(int expected, MvcResult result) {
        assertEquals(expected, envelope(result).get("code"),
                new String(result.getResponse().getContentAsByteArray(), java.nio.charset.StandardCharsets.UTF_8));
    }
}
