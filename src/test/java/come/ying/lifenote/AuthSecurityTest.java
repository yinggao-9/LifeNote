package come.ying.lifenote;

import come.ying.lifenote.common.utils.JwtUtils;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Authentication, token handling and the login-related security contract.
 * Malicious-intent cases: forged, tampered, expired and wrongly-signed
 * tokens must all be refused with a bare 401 before any controller runs.
 */
class AuthSecurityTest extends AbstractIntegrationTest {

    /** Same value as src/test/resources/application-test.yml jwt.secret-key. */
    private static final String TEST_SECRET = "lifenote-test-secret-key-0123456789abcdef0123456789";

    @Test
    void registerThenLoginWithCorrectPassword() throws Exception {
        String email = "auth-" + UUID.randomUUID() + "@test.com";
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"username\":\"alice\","
                                + "\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.email").value(email))
                .andExpect(jsonPath("$.data.token").isNotEmpty());
    }

    @Test
    void duplicateEmailIsRefused() throws Exception {
        TestUser user = createUser();
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + user.email() + "\",\"username\":\"copycat\","
                                + "\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1006));
    }

    @Test
    void wrongPasswordIsRefused() throws Exception {
        TestUser user = createUser();
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + user.email() + "\",\"password\":\"TotallyWrong1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1001));
    }

    @Test
    void unknownEmailGivesTheSameAnswerAsWrongPassword() throws Exception {
        // 1001 for both cases: the API must not reveal which emails are registered
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nobody-" + UUID.randomUUID() + "@test.com\","
                                + "\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1001));
    }

    @Test
    void protectedEndpointWithoutTokenIs401() throws Exception {
        mockMvc.perform(get("/api/tasks"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void garbageTokenIs401() throws Exception {
        mockMvc.perform(get("/api/tasks").header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void expiredTokenIs401() throws Exception {
        String expired = JwtUtils.createJwt(TEST_SECRET, -5_000,
                Map.of("userId", UUID.randomUUID().toString()));
        mockMvc.perform(get("/api/tasks").header("Authorization", "Bearer " + expired))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenSignedWithAnotherSecretIs401() throws Exception {
        String forged = JwtUtils.createJwt("another-secret-key-9876543210abcdef0123456789",
                60_000, Map.of("userId", UUID.randomUUID().toString()));
        mockMvc.perform(get("/api/tasks").header("Authorization", "Bearer " + forged))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tamperedTokenIs401() throws Exception {
        String token = login(createUser().email(), PASSWORD);
        String tampered = token.substring(0, token.length() - 2) + "ab";
        mockMvc.perform(get("/api/tasks").header("Authorization", "Bearer " + tampered))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutWithoutTokenIs401() throws Exception {
        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void legacyBareTokenHeaderIsAlsoAccepted() throws Exception {
        TestUser user = createUser();
        mockMvc.perform(get("/api/tasks").header("token", user.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void passwordChangeInvalidatesTheOldPassword() throws Exception {
        TestUser user = createUser();
        mockMvc.perform(put("/api/users/me/password")
                        .header("Authorization", "Bearer " + user.token())
                        .param("password", "NewPassw0rd!"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + user.email() + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1001));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + user.email() + "\",\"password\":\"NewPassw0rd!\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void registerValidationRejectsWeakBodies() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"broken\",\"username\":\"x\",\"password\":\"short\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void loginValidationRejectsBlankPassword() throws Exception {
        TestUser user = createUser();
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + user.email() + "\",\"password\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void currentUserInfoMatchesTheLogin() throws Exception {
        TestUser user = createUser();
        var result = auth(user, post("/api/users/me")).andReturn();
        assertEquals(200, envelope(result).get("code"));
        assertEquals(user.email(), data(result).get("email"));
    }
}
