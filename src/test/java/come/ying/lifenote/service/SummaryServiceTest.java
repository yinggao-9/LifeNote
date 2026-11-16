package come.ying.lifenote.service;

import come.ying.lifenote.common.enums.TaskStatus;
import come.ying.lifenote.dto.response.SummaryResponse;
import come.ying.lifenote.entity.Postponement;
import come.ying.lifenote.entity.SummaryLlmCache;
import come.ying.lifenote.entity.Task;
import come.ying.lifenote.exception.BusinessException;
import come.ying.lifenote.repository.PostponementRepository;
import come.ying.lifenote.repository.SummaryLlmCacheRepository;
import come.ying.lifenote.repository.TaskRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Rule-based counts, the daily series, and the cached-LLM paragraph contract:
 * success caches once, failure degrades to null and caches nothing.
 */
class SummaryServiceTest {

    private final TaskRepository tasks = mock(TaskRepository.class);
    private final PostponementRepository postponements = mock(PostponementRepository.class);
    private final SummaryLlmCacheRepository cache = mock(SummaryLlmCacheRepository.class);
    private final LlmChatService llm = mock(LlmChatService.class);

    private final UUID userId = UUID.randomUUID();
    private final SummaryService service =
            new SummaryService(tasks, postponements, cache, llm);

    private static Task task(TaskStatus status, LocalDate due) {
        Task t = new Task();
        t.setStatus(status);
        t.setDueDate(due);
        t.setTitle("task-" + due);
        return t;
    }

    private static Postponement log(UUID taskId, LocalDate oldDue) {
        Postponement p = new Postponement();
        p.setTaskId(taskId);
        p.setOldDueDate(oldDue);
        return p;
    }

    private void stubTasks(List<Task> rows) {
        when(tasks.findByUserIdAndDueDateBetweenOrderByDueDateAscDueTimeAsc(
                any(UUID.class), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(rows);
    }

    private void stubLogs(List<Postponement> rows) {
        when(postponements.findByUserIdAndOldDueDateBetweenOrderByCreatedAtDesc(
                any(UUID.class), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(rows);
    }

    /** The service asks the model for its preferred provider before every call. */
    private void stubLlmReply(String text) {
        when(llm.preferredModelFor(any(UUID.class))).thenReturn("gpt");
        when(llm.chat(anyString(), anyString(), any()))
                .thenReturn(new LlmChatService.Reply(text, "gpt", "gpt-4o-mini"));
    }

    private void stubLlmFailure() {
        when(llm.preferredModelFor(any(UUID.class))).thenReturn("gpt");
        when(llm.chat(anyString(), anyString(), any()))
                .thenThrow(new BusinessException(1103, "AI down"));
    }

    @Test
    void countsRateAndDailySeriesAreCorrect() {
        Task done1 = task(TaskStatus.DONE, LocalDate.of(2026, 11, 5));
        Task done2 = task(TaskStatus.DONE, LocalDate.of(2026, 11, 10));
        Task todo = task(TaskStatus.TODO, LocalDate.of(2026, 11, 15));
        Task cancelled = task(TaskStatus.CANCELLED, LocalDate.of(2026, 11, 20));
        Task postponed = task(TaskStatus.POSTPONED, LocalDate.of(2026, 11, 25));
        stubTasks(List.of(done1, done2, todo, cancelled, postponed));
        stubLogs(List.of());
        when(cache.findByUserIdAndMonth(any(UUID.class), anyString()))
                .thenReturn(Optional.empty());
        stubLlmReply("A solid month.");

        SummaryResponse r = service.summary(userId, "2026-11");

        assertEquals(5, r.getCounts().get("total"));
        assertEquals(2, r.getCounts().get("done"));
        assertEquals(1, r.getCounts().get("cancelled"));
        assertEquals(1, r.getCounts().get("postponed"));
        assertEquals(1, r.getCounts().get("todo"));
        assertEquals(0.4, r.getCompletionRate());
        assertEquals(30, r.getDaily().size(), "November has 30 days");
        SummaryResponse.DailyCount day5 = r.getDaily().get(4);
        assertEquals(LocalDate.of(2026, 11, 5), day5.getDate());
        assertEquals(1, day5.getTotal());
        assertEquals(1, day5.getDone());
        assertEquals("A solid month.", r.getLlmSummary());
    }

    @Test
    void emptyMonthHasZeroRateAndStillAnswers() {
        stubTasks(List.of());
        stubLogs(List.of());
        when(cache.findByUserIdAndMonth(any(UUID.class), anyString()))
                .thenReturn(Optional.empty());
        stubLlmReply("Quiet month.");

        SummaryResponse r = service.summary(userId, "2026-11");

        assertEquals(0, r.getCounts().get("total"));
        assertEquals(0.0, r.getCompletionRate());
        assertEquals(30, r.getDaily().size());
        assertTrue(r.getDaily().stream().allMatch(d -> d.getTotal() == 0));
        assertEquals("Quiet month.", r.getLlmSummary());
    }

    @Test
    void invalidMonthRaises1109() {
        for (String bad : new String[]{"abc", "2026/11", "2026-13", "11-2026"}) {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.summary(userId, bad), bad);
            assertEquals(1109, ex.getCode(), bad);
        }
    }

    @Test
    void llmSuccessIsCachedAndSecondCallSkipsTheModel() {
        stubTasks(List.of());
        stubLogs(List.of());
        when(cache.findByUserIdAndMonth(any(UUID.class), anyString()))
                .thenReturn(Optional.empty(), Optional.of(cachedRow("Cached paragraph.")));
        stubLlmReply("Fresh paragraph.");

        SummaryResponse first = service.summary(userId, "2026-11");
        SummaryResponse second = service.summary(userId, "2026-11");

        assertEquals("Fresh paragraph.", first.getLlmSummary());
        assertEquals("Cached paragraph.", second.getLlmSummary());
        verify(llm, times(1)).chat(anyString(), anyString(), any());
        verify(cache, times(1)).save(any(SummaryLlmCache.class));
    }

    @Test
    void llmFailureDegradesToNullAndCachesNothing() {
        stubTasks(List.of());
        stubLogs(List.of());
        when(cache.findByUserIdAndMonth(any(UUID.class), anyString()))
                .thenReturn(Optional.empty());
        stubLlmFailure();

        SummaryResponse r = service.summary(userId, "2026-11");

        assertNull(r.getLlmSummary());
        assertEquals(0, r.getCounts().get("total"), "numbers must survive an LLM outage");
        verify(cache, never()).save(any(SummaryLlmCache.class));
    }

    @Test
    void blankReplyIsNotCached() {
        stubTasks(List.of());
        stubLogs(List.of());
        when(cache.findByUserIdAndMonth(any(UUID.class), anyString()))
                .thenReturn(Optional.empty());
        stubLlmReply("   ");

        SummaryResponse r = service.summary(userId, "2026-11");

        assertNull(r.getLlmSummary());
        verify(cache, never()).save(any(SummaryLlmCache.class));
    }

    @Test
    void topPostponedIsSortedByCountAndCappedAtFive() {
        List<Task> monthTasks = new ArrayList<>();
        List<Postponement> logs = new ArrayList<>();
        Task top = task(TaskStatus.TODO, LocalDate.of(2026, 11, 3));
        monthTasks.add(top);
        for (int i = 0; i < 3; i++) logs.add(log(top.getId(), top.getDueDate()));
        for (int i = 1; i <= 5; i++) {
            Task t = task(TaskStatus.TODO, LocalDate.of(2026, 11, 3).plusDays(i));
            monthTasks.add(t);
            logs.add(log(t.getId(), t.getDueDate()));   // one event each
        }
        stubTasks(monthTasks);
        stubLogs(logs);
        when(cache.findByUserIdAndMonth(any(UUID.class), anyString()))
                .thenReturn(Optional.empty());
        when(llm.preferredModelFor(any(UUID.class))).thenReturn("gpt");
        when(llm.chat(anyString(), anyString(), any()))
                .thenReturn(new LlmChatService.Reply(null, "gpt", "gpt-4o-mini"));

        SummaryResponse r = service.summary(userId, "2026-11");

        assertEquals(8, r.getPostponementCount());
        assertEquals(5, r.getTopPostponedItems().size(), "cap at five");
        assertEquals(3, r.getTopPostponedItems().get(0).getTimes());
        assertEquals(top.getTitle(), r.getTopPostponedItems().get(0).getTitle());
        assertNotNull(r.getTopPostponedItems().get(0).getItemId());
    }

    private SummaryLlmCache cachedRow(String content) {
        SummaryLlmCache row = new SummaryLlmCache();
        row.setUserId(userId);
        row.setMonth("2026-11");
        row.setContent(content);
        return row;
    }
}
