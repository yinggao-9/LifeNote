package come.ying.lifenote;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves the integration-test machinery itself works: the Testcontainers
 * PostgreSQL came up, Flyway built the schema, the "test" profile applied,
 * and MockMvc can drive a full register -> login round trip.
 */
class SmokeTest extends AbstractIntegrationTest {

    @Test
    void registerThenLoginReturnsToken() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"smoke@test.com\",\"username\":\"smoker\",\"password\":\"Passw0rd!\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"smoke@test.com\",\"password\":\"Passw0rd!\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.token").isNotEmpty());
    }
}
