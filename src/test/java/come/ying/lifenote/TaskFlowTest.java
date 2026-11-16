package come.ying.lifenote;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * System tests for tasks, board, postponement log and .ics export
 * (endpoints 10-19): the three creation paths, the status machine, the
 * four-write postpone transaction and the read-only views.
 */
class TaskFlowTest extends AbstractIntegrationTest {

    private String createTask(TestUser user, String title, String dueDate, String dueTime) throws Exception {
        String body = "{\"title\":\"" + title + "\",\"dueDate\":\"" + dueDate + "\""
                + (dueTime == null ? "" : ",\"dueTime\":\"" + dueTime + "\"") + "}";
        MvcResult result = auth(user, post("/api/tasks").contentType("application/json").content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        return data(result).get("id").toString();
    }

    // ---------- creation paths ----------

    @Test
    void quickAddParsesTheSentenceWithoutAi() throws Exception {
        TestUser user = createUser();
        String tomorrow = LocalDate.now().plusDays(1).toString();

        MvcResult result = auth(user, post("/api/tasks/quick-add")
                        .contentType("application/json")
                        .content("{\"text\":\"Pay rent tomorrow at 10:00\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("OK"))
                .andExpect(jsonPath("$.data.task.dueDate").value(tomorrow))
                .andExpect(jsonPath("$.data.task.dueTime").value("10:00:00"))
                .andExpect(jsonPath("$.data.task.source").value("QUICK"))
                .andReturn();
        assertTrue(data(result).get("task").toString().contains("Pay rent"),
                data(result).get("task").toString());
    }

    @Test
    void quickAddWithoutADateAsksForMoreInputInsteadOfSaving() throws Exception {
        TestUser user = createUser();

        MvcResult result = auth(user, post("/api/tasks/quick-add")
                        .contentType("application/json")
                        .content("{\"text\":\"buy milk\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("NEED_INPUT"))
                .andReturn();
        assertNull(data(result).get("task"));

        auth(user, get("/api/tasks"))
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void manualCreateThenListByRangeAndStatus() throws Exception {
        TestUser user = createUser();
        createTask(user, "write report", "2026-12-20", null);
        createTask(user, "call dentist", "2026-12-22", "09:30");

        auth(user, get("/api/tasks").param("from", "2026-12-01").param("to", "2026-12-31"))
                .andExpect(jsonPath("$.data.length()").value(2));
        auth(user, get("/api/tasks").param("from", "2026-12-21").param("to", "2026-12-31"))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].title").value("call dentist"));
        auth(user, get("/api/tasks").param("from", "2026-12-01").param("to", "2026-12-31")
                        .param("status", "DONE"))
                .andExpect(jsonPath("$.data.length()").value(0));
        auth(user, get("/api/tasks").param("status", "NOT_A_STATUS"))
                .andExpect(jsonPath("$.code").value(1203));
    }

    @Test
    void quickAddRejectsOversizedInput() throws Exception {
        TestUser user = createUser();
        auth(user, post("/api/tasks/quick-add")
                        .contentType("application/json")
                        .content("{\"text\":\"" + "x".repeat(501) + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    // ---------- edit and status machine ----------

    @Test
    void patchChangesOnlyTheGivenFields() throws Exception {
        TestUser user = createUser();
        String id = createTask(user, "original title", "2026-12-20", null);

        auth(user, patch("/api/tasks/" + id)
                        .contentType("application/json")
                        .content("{\"notes\":\"new notes\",\"place\":\"library\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("original title"))
                .andExpect(jsonPath("$.data.notes").value("new notes"))
                .andExpect(jsonPath("$.data.place").value("library"))
                .andExpect(jsonPath("$.data.dueDate").value("2026-12-20"));

        auth(user, patch("/api/tasks/" + id)
                        .contentType("application/json")
                        .content("{\"title\":\"   \"}"))
                .andExpect(jsonPath("$.code").value(1109));
    }

    @Test
    void statusTransitionsTrackCompletion() throws Exception {
        TestUser user = createUser();
        String id = createTask(user, "exercise", "2026-12-20", null);

        auth(user, post("/api/tasks/" + id + "/status")
                        .contentType("application/json").content("{\"status\":\"DONE\"}"))
                .andExpect(jsonPath("$.data.status").value("DONE"))
                .andExpect(jsonPath("$.data.completedAt").isNotEmpty());

        MvcResult backToTodo = auth(user, post("/api/tasks/" + id + "/status")
                        .contentType("application/json").content("{\"status\":\"TODO\"}"))
                .andExpect(jsonPath("$.data.status").value("TODO"))
                .andReturn();
        assertNull(data(backToTodo).get("completedAt"), "restoring TODO clears completedAt");

        auth(user, post("/api/tasks/" + id + "/status")
                        .contentType("application/json").content("{\"status\":\"CANCELLED\"}"))
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));
    }

    @Test
    void postponedStatusCannotBeSetByHand() throws Exception {
        TestUser user = createUser();
        String id = createTask(user, "sneaky", "2026-12-20", null);

        auth(user, post("/api/tasks/" + id + "/status")
                        .contentType("application/json").content("{\"status\":\"POSTPONED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1203));
        auth(user, post("/api/tasks/" + id + "/status")
                        .contentType("application/json").content("{\"status\":\"BOGUS\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1203));
    }

    // ---------- postpone ----------

    @Test
    void postponeMovesTheTaskAndWritesTheLog() throws Exception {
        TestUser user = createUser();
        String id = createTask(user, "submit assignment", LocalDate.now().plusDays(1).toString(), null);

        MvcResult moved = auth(user, post("/api/tasks/" + id + "/postpone")
                        .contentType("application/json")
                        .content("{\"dueDate\":\"" + LocalDate.now().plusDays(7) + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("TODO"))
                .andExpect(jsonPath("$.data.dueDate").value(LocalDate.now().plusDays(7).toString()))
                .andReturn();
        String copyId = data(moved).get("id").toString();

        // the original stays behind as history, linked to the copy
        auth(user, get("/api/tasks/" + id))
                .andExpect(jsonPath("$.data.status").value("POSTPONED"));
        auth(user, get("/api/tasks/" + copyId))
                .andExpect(jsonPath("$.data.postponedFromTaskId").value(id));

        MvcResult log = auth(user, get("/api/postponements")
                        .param("from", LocalDate.now().minusDays(1).toString())
                        .param("to", LocalDate.now().plusDays(30).toString()))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andReturn();
        Map<?, ?> row = (Map<?, ?>) dataList(log).get(0);
        assertEquals(id, row.get("taskId"));
        assertEquals(copyId, row.get("newTaskId"));
    }

    @Test
    void postponeRulesRejectBadDates() throws Exception {
        TestUser user = createUser();
        String due = LocalDate.now().plusDays(1).toString();
        String id = createTask(user, "fussy task", due, null);

        // same date
        auth(user, post("/api/tasks/" + id + "/postpone")
                        .contentType("application/json").content("{\"dueDate\":\"" + due + "\"}"))
                .andExpect(jsonPath("$.code").value(1202));
        // in the past
        auth(user, post("/api/tasks/" + id + "/postpone")
                        .contentType("application/json")
                        .content("{\"dueDate\":\"" + LocalDate.now().minusDays(1) + "\"}"))
                .andExpect(jsonPath("$.code").value(1202));

        // a cancelled task cannot move
        auth(user, post("/api/tasks/" + id + "/status")
                        .contentType("application/json").content("{\"status\":\"CANCELLED\"}"))
                .andExpect(jsonPath("$.code").value(200));
        auth(user, post("/api/tasks/" + id + "/postpone")
                        .contentType("application/json")
                        .content("{\"dueDate\":\"" + LocalDate.now().plusDays(5) + "\"}"))
                .andExpect(jsonPath("$.code").value(1102));
    }

    // ---------- board and export ----------

    @Test
    void boardBucketsTheDayAndExcludesPostponedFromTheTotal() throws Exception {
        TestUser user = createUser();
        String today = LocalDate.now().toString();
        createTask(user, "todo one", today, null);
        createTask(user, "todo two", today, null);
        String doneId = createTask(user, "done one", today, null);
        String cancelledId = createTask(user, "cancelled one", today, null);
        String movedId = createTask(user, "postponed one", today, null);

        auth(user, post("/api/tasks/" + doneId + "/status")
                        .contentType("application/json").content("{\"status\":\"DONE\"}"))
                .andExpect(jsonPath("$.code").value(200));
        auth(user, post("/api/tasks/" + cancelledId + "/status")
                        .contentType("application/json").content("{\"status\":\"CANCELLED\"}"))
                .andExpect(jsonPath("$.code").value(200));
        auth(user, post("/api/tasks/" + movedId + "/postpone")
                        .contentType("application/json")
                        .content("{\"dueDate\":\"" + LocalDate.now().plusDays(3) + "\"}"))
                .andExpect(jsonPath("$.code").value(200));

        auth(user, get("/api/boards").param("date", today))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(4))
                .andExpect(jsonPath("$.data.todo").value(2))
                .andExpect(jsonPath("$.data.done").value(1))
                .andExpect(jsonPath("$.data.cancelled").value(1))
                .andExpect(jsonPath("$.data.postponed").value(1))
                .andExpect(jsonPath("$.data.todoTasks.length()").value(2));
    }

    @Test
    void icsExportContainsOneEventPerTask() throws Exception {
        TestUser user = createUser();
        String allDay = createTask(user, "Pay rent", "2026-12-16", null);
        String timed = createTask(user, "Dentist appointment", "2026-12-15", "10:30");

        MvcResult result = auth(user, get("/api/tasks/export.ics")
                        .param("from", "2026-12-01").param("to", "2026-12-31"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/calendar"))
                .andReturn();
        String ics = new String(result.getResponse().getContentAsByteArray(), java.nio.charset.StandardCharsets.UTF_8);

        assertTrue(ics.contains("BEGIN:VEVENT"), ics);
        assertTrue(ics.contains("SUMMARY:Pay rent"), ics);
        assertTrue(ics.contains("SUMMARY:Dentist appointment"), ics);
        // UID is stable so re-imports update instead of duplicating
        assertTrue(ics.contains("UID:" + allDay + "@lifenote"), ics);
        assertTrue(ics.contains("UID:" + timed + "@lifenote"), ics);
        // all-day events end exclusively the next day; timed events default to one hour
        assertTrue(ics.contains("20261216"), ics);
        assertTrue(ics.contains("20261217"), ics);
        assertTrue(ics.contains("20261215T103000"), ics);
        assertTrue(ics.contains("20261215T113000"), ics);
    }

    // ---------- cross-user isolation ----------

    @Test
    void anotherUserCannotTouchMyTasks() throws Exception {
        TestUser alice = createUser();
        TestUser bob = createUser();
        String id = createTask(alice, "private task", LocalDate.now().plusDays(1).toString(), null);

        auth(bob, get("/api/tasks/" + id))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1201));
        auth(bob, patch("/api/tasks/" + id)
                        .contentType("application/json").content("{\"title\":\"hijacked\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1201));
        auth(bob, post("/api/tasks/" + id + "/status")
                        .contentType("application/json").content("{\"status\":\"DONE\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1201));
        auth(bob, post("/api/tasks/" + id + "/postpone")
                        .contentType("application/json")
                        .content("{\"dueDate\":\"" + LocalDate.now().plusDays(9) + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1201));

        // and Bob's list and board stay empty
        auth(bob, get("/api/tasks"))
                .andExpect(jsonPath("$.data.length()").value(0));
        auth(bob, get("/api/boards"))
                .andExpect(jsonPath("$.data.total").value(0));

        // Alice's task is unchanged
        auth(alice, get("/api/tasks/" + id))
                .andExpect(jsonPath("$.data.title").value("private task"))
                .andExpect(jsonPath("$.data.status").value("TODO"));
    }
}
