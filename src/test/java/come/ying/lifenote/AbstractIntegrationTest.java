package come.ying.lifenote;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;

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
}
