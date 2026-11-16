package come.ying.lifenote;

import come.ying.lifenote.service.LlmChatService;
import come.ying.lifenote.service.ReminderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Account-level security and privacy (proposal 5.4): deletion removes the
 * user and everything attached, and no list endpoint ever shows one user
 * the data of another.
 */
class AccountSecurityTest extends AbstractIntegrationTest {

    @Autowired
    private ReminderService reminderService;

    @Test
    void deletingAnAccountRequiresItsPassword() throws Exception {
        TestUser user = createUser();
        String taskId = createTask(user, "survivor", tomorrow());

        auth(user, delete("/api/users/me")
                        .contentType("application/json")
                        .content("{\"password\":\"NotMyPassword\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1001));

        // still there
        auth(user, get("/api/tasks/" + taskId))
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void deletingAnAccountRemovesTheUserAndAllItsData() throws Exception {
        when(llmChatService.preferredModelFor(any())).thenReturn("gpt");
        when(llmChatService.chat(anyString(), anyString(), any()))
                .thenReturn(new LlmChatService.Reply("[]", "gpt", "gpt-4o-mini"));
        TestUser user = createUser();

        // something to delete: a task, a document and a reminder
        String taskId = createTask(user, "doomed task", tomorrow());
        MvcResult doc = auth(user, post("/api/documents").param("text", "doomed letter"))
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        assertNotNull(data(doc).get("documentId"));
        reminderService.generateDeadlineReminders();
        auth(user, get("/api/reminders"))
                .andExpect(jsonPath("$.data.length()").value(1));

        auth(user, delete("/api/users/me")
                        .contentType("application/json")
                        .content("{\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        // the stored originals were wiped before the rows disappeared
        verify(s3StorageService).deleteAllForUser(any(UUID.class));

        // the account cannot be used or logged into again
        auth(user, post("/api/users/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1002));
        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("{\"email\":\"" + user.email() + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1001));
    }

    @Test
    void noListEndpointLeaksAnotherUsersData() throws Exception {
        when(llmChatService.preferredModelFor(any())).thenReturn("gpt");
        when(llmChatService.chat(anyString(), anyString(), any()))
                .thenReturn(new LlmChatService.Reply("[]", "gpt", "gpt-4o-mini"));
        TestUser alice = createUser();
        TestUser bob = createUser();

        createTask(alice, "alice task", tomorrow());
        auth(alice, post("/api/documents").param("text", "alice letter"))
                .andExpect(jsonPath("$.code").value(200));
        reminderService.generateDeadlineReminders();

        // Bob sees his own (empty) world only
        auth(bob, get("/api/tasks").param("from", "2000-01-01").param("to", "2100-01-01"))
                .andExpect(jsonPath("$.data.length()").value(0));
        auth(bob, get("/api/documents"))
                .andExpect(jsonPath("$.data.length()").value(0));
        auth(bob, get("/api/reminders"))
                .andExpect(jsonPath("$.data.length()").value(0));
        auth(bob, get("/api/boards"))
                .andExpect(jsonPath("$.data.total").value(0));

        // while Alice's data is still intact
        auth(alice, get("/api/tasks").param("from", "2000-01-01").param("to", "2100-01-01"))
                .andExpect(jsonPath("$.data.length()").value(1));
        auth(alice, get("/api/documents"))
                .andExpect(jsonPath("$.data.length()").value(1));
        auth(alice, get("/api/reminders"))
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    void profileUpdatesOnlyAffectTheCaller() throws Exception {
        TestUser alice = createUser();
        TestUser bob = createUser();

        auth(alice, org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .patch("/api/users/me").param("username", "alice2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("alice2"));

        auth(bob, post("/api/users/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("tester"));
    }

    private String createTask(TestUser user, String title, String dueDate) throws Exception {
        MvcResult created = auth(user, post("/api/tasks")
                        .contentType("application/json")
                        .content("{\"title\":\"" + title + "\",\"dueDate\":\"" + dueDate + "\"}"))
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        return data(created).get("id").toString();
    }

    /** Tomorrow in the user's default time zone (Europe/Dublin), the day the deadline job targets. */
    private static String tomorrow() {
        return LocalDate.now(ZoneId.of("Europe/Dublin")).plusDays(1).toString();
    }
}
