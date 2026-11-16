package come.ying.lifenote;

import come.ying.lifenote.service.LlmChatService;
import come.ying.lifenote.service.ReminderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * System tests for the monthly summary (proposal 5.3 pass condition:
 * the numbers must match the seeded data exactly) and the reminder
 * pipeline: deadline pass, evening digest, read tracking and ownership.
 */
class SummaryReminderTest extends AbstractIntegrationTest {

    @Autowired
    private ReminderService reminderService;

    private static final DateTimeFormatter HMS = DateTimeFormatter.ofPattern("HH:mm:ss");

    private String createTask(TestUser user, String title, String dueDate, String status) throws Exception {
        MvcResult created = auth(user, post("/api/tasks")
                        .contentType("application/json")
                        .content("{\"title\":\"" + title + "\",\"dueDate\":\"" + dueDate + "\"}"))
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        String id = data(created).get("id").toString();
        if (status != null && !status.equals("TODO")) {
            auth(user, post("/api/tasks/" + id + "/status")
                            .contentType("application/json")
                            .content("{\"status\":\"" + status + "\"}"))
                    .andExpect(jsonPath("$.code").value(200));
        }
        return id;
    }

    // ---------- monthly summary ----------

    @Test
    void summaryNumbersMatchTheSeededDataExactly() throws Exception {
        when(llmChatService.preferredModelFor(any())).thenReturn("gpt");
        when(llmChatService.chat(anyString(), anyString(), any()))
                .thenReturn(new LlmChatService.Reply("A steady month with one item kept moving.",
                        "gpt", "gpt-4o-mini"));
        TestUser user = createUser();

        // 3 done, 1 cancelled, 2 todo; one of the todos is postponed into a copy
        createTask(user, "done a", "2026-12-03", "DONE");
        createTask(user, "done b", "2026-12-08", "DONE");
        createTask(user, "done c", "2026-12-09", "DONE");
        createTask(user, "cancelled a", "2026-12-12", "CANCELLED");
        createTask(user, "todo a", "2026-12-15", null);
        String moved = createTask(user, "moved a", "2026-12-10", null);
        auth(user, post("/api/tasks/" + moved + "/postpone")
                        .contentType("application/json")
                        .content("{\"dueDate\":\"2026-12-20\"}"))
                .andExpect(jsonPath("$.code").value(200));

        MvcResult result = auth(user, get("/api/summary").param("month", "2026-12"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.month").value("2026-12"))
                .andExpect(jsonPath("$.data.counts.total").value(7))
                .andExpect(jsonPath("$.data.counts.done").value(3))
                .andExpect(jsonPath("$.data.counts.cancelled").value(1))
                .andExpect(jsonPath("$.data.counts.postponed").value(1))
                .andExpect(jsonPath("$.data.counts.todo").value(2))
                .andExpect(jsonPath("$.data.postponementCount").value(1))
                .andExpect(jsonPath("$.data.daily.length()").value(31))
                .andReturn();

        Map<String, Object> summary = data(result);
        assertEquals(3.0 / 7, ((Number) summary.get("completionRate")).doubleValue(), 1e-9);
        assertEquals("A steady month with one item kept moving.", summary.get("llmSummary"));

        List<?> top = (List<?>) summary.get("topPostponedItems");
        assertEquals(1, top.size());
        assertEquals("moved a", ((Map<?, ?>) top.get(0)).get("title"));
        assertEquals(1, ((Map<?, ?>) top.get(0)).get("times"));
    }

    @Test
    void summaryParagraphIsCachedAfterTheFirstCall() throws Exception {
        when(llmChatService.preferredModelFor(any())).thenReturn("gpt");
        when(llmChatService.chat(anyString(), anyString(), any()))
                .thenReturn(new LlmChatService.Reply("Cached paragraph.", "gpt", "gpt-4o-mini"));
        TestUser user = createUser();

        auth(user, get("/api/summary").param("month", "2027-01"))
                .andExpect(jsonPath("$.data.llmSummary").value("Cached paragraph."));
        auth(user, get("/api/summary").param("month", "2027-01"))
                .andExpect(jsonPath("$.data.llmSummary").value("Cached paragraph."));

        // the model was asked once; the second read comes from the cache
        verify(llmChatService, times(1)).chat(anyString(), anyString(), any());
    }

    @Test
    void summaryNumbersSurviveAnLlmOutage() throws Exception {
        when(llmChatService.preferredModelFor(any())).thenReturn("gpt");
        when(llmChatService.chat(anyString(), anyString(), any()))
                .thenThrow(new come.ying.lifenote.exception.BusinessException(1103, "AI down"));
        TestUser user = createUser();
        createTask(user, "only task", "2027-02-10", "DONE");

        MvcResult result = auth(user, get("/api/summary").param("month", "2027-02"))
                .andExpect(jsonPath("$.data.counts.done").value(1))
                .andReturn();

        // the numbers are rule-based: a dead LLM must not take them hostage
        assertNull(data(result).get("llmSummary"));
    }

    @Test
    void badMonthFormatIs1109() throws Exception {
        TestUser user = createUser();
        auth(user, get("/api/summary").param("month", "2026/12"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1109));
    }

    // ---------- reminders ----------

    @Test
    void deadlineReminderFiresForTomorrowOnlyAndIsIdempotent() throws Exception {
        TestUser user = createUser();
        String tomorrow = LocalDate.now(ZoneId.of("Europe/Dublin")).plusDays(1).toString();
        createTask(user, "hand in essay", tomorrow, null);
        createTask(user, "far away", LocalDate.now().plusDays(5).toString(), null);

        reminderService.generateDeadlineReminders();
        reminderService.generateDeadlineReminders();   // the hourly job may overlap

        MvcResult list = auth(user, get("/api/reminders"))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andReturn();
        Map<?, ?> reminder = (Map<?, ?>) dataList(list).get(0);
        assertEquals("DEADLINE", reminder.get("type"));
        assertTrue(reminder.get("title").toString().contains("hand in essay"));
        assertEquals(tomorrow, reminder.get("remindForDate"));
    }

    @Test
    void eveningDigestListsOpenTasksAndDoesNotRepeat() throws Exception {
        TestUser user = createUser();
        String now = LocalTime.now(ZoneId.of("Europe/Dublin")).withSecond(0).withNano(0).format(HMS);
        auth(user, put("/api/users/me/settings")
                        .contentType("application/json")
                        .content("{\"timeZone\":\"Europe/Dublin\","
                                + "\"eveningReminderTime\":\"" + now + "\","
                                + "\"emailRemindersEnabled\":true,\"aiModel\":\"gpt\"}"))
                .andExpect(jsonPath("$.code").value(200));

        String today = LocalDate.now(ZoneId.of("Europe/Dublin")).toString();
        createTask(user, "open task one", today, null);
        createTask(user, "open task two", today, null);
        createTask(user, "finished task", today, "DONE");

        reminderService.generateEveningReminders();
        reminderService.generateEveningReminders();   // the per-minute job may overlap

        MvcResult list = auth(user, get("/api/reminders"))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andReturn();
        Map<?, ?> digest = (Map<?, ?>) dataList(list).get(0);
        assertEquals("EVENING", digest.get("type"));
        String body = digest.get("body").toString();
        assertTrue(body.contains("Still open today"), body);
        assertTrue(body.contains("open task one"), body);
        assertTrue(body.contains("open task two"), body);
        verify(reminderEmailService, times(1)).send(any());
    }

    @Test
    void remindersCanBeMarkedReadOnlyByTheirOwner() throws Exception {
        TestUser user = createUser();
        String tomorrow = LocalDate.now(ZoneId.of("Europe/Dublin")).plusDays(1).toString();
        createTask(user, "read me", tomorrow, null);
        reminderService.generateDeadlineReminders();

        MvcResult list = auth(user, get("/api/reminders").param("unreadOnly", "true"))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andReturn();
        String reminderId = ((Map<?, ?>) dataList(list).get(0)).get("id").toString();

        TestUser other = createUser();
        auth(other, post("/api/reminders/" + reminderId + "/read"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1301));

        auth(user, post("/api/reminders/" + reminderId + "/read"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        auth(user, get("/api/reminders").param("unreadOnly", "true"))
                .andExpect(jsonPath("$.data.length()").value(0));
        auth(user, get("/api/reminders"))
                .andExpect(jsonPath("$.data[0].read").value(true));
    }

    // ---------- settings ----------

    @Test
    void settingsRoundTripAndValidation() throws Exception {
        TestUser user = createUser();

        auth(user, put("/api/users/me/settings")
                        .contentType("application/json")
                        .content("{\"timeZone\":\"Asia/Shanghai\","
                                + "\"eveningReminderTime\":\"20:30:00\","
                                + "\"emailRemindersEnabled\":false,\"aiModel\":\"deepseek\"}"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.timeZone").value("Asia/Shanghai"))
                .andExpect(jsonPath("$.data.aiModel").value("deepseek"));

        auth(user, put("/api/users/me/settings")
                        .contentType("application/json")
                        .content("{\"timeZone\":\"Mars/Olympus\","
                                + "\"eveningReminderTime\":\"20:30:00\","
                                + "\"emailRemindersEnabled\":false,\"aiModel\":\"gpt\"}"))
                .andExpect(jsonPath("$.code").value(1007));

        auth(user, put("/api/users/me/settings")
                        .contentType("application/json")
                        .content("{\"timeZone\":\"Europe/Dublin\","
                                + "\"eveningReminderTime\":\"20:30:00\","
                                + "\"emailRemindersEnabled\":false,\"aiModel\":\"skynet\"}"))
                .andExpect(jsonPath("$.code").value(1008));
    }
}
