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
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** The monthly summary: one range read of tasks plus one of the postpone log, bucketed in memory. */
@Slf4j
@Service
public class SummaryService {

    private final TaskRepository taskRepository;
    private final PostponementRepository postponementRepository;
    private final SummaryLlmCacheRepository cacheRepository;
    private final ChatClient chatClient;

    public SummaryService(TaskRepository taskRepository,
                          PostponementRepository postponementRepository,
                          SummaryLlmCacheRepository cacheRepository,
                          ChatClient chatClient) {
        this.taskRepository = taskRepository;
        this.postponementRepository = postponementRepository;
        this.cacheRepository = cacheRepository;
        this.chatClient = chatClient;
    }

    public SummaryResponse summary(UUID userId, String month) {
        YearMonth ym;
        try {
            ym = YearMonth.parse(month);          // rejects anything that is not yyyy-MM
        } catch (Exception e) {
            throw new BusinessException(1109, "month must be in yyyy-MM format.");
        }
        LocalDate first = ym.atDay(1);
        LocalDate last = ym.atEndOfMonth();

        List<Task> tasks = taskRepository
                .findByUserIdAndDueDateBetweenOrderByDueDateAscDueTimeAsc(userId, first, last);

        int done = 0, cancelled = 0, postponed = 0, todo = 0;
        for (Task t : tasks) {
            switch (t.getStatus()) {
                case DONE      -> done++;
                case CANCELLED -> cancelled++;
                case POSTPONED -> postponed++;
                case TODO      -> todo++;
            }
        }
        int total = tasks.size();
        double completionRate = total == 0 ? 0 : (double) done / total;

        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("total", total);
        counts.put("done", done);
        counts.put("cancelled", cancelled);
        counts.put("postponed", postponed);
        counts.put("todo", todo);

        // log rows are keyed by the original due date - "which plan moved",
        // not "when the button was pressed"
        List<Postponement> logs = postponementRepository
                .findByUserIdAndOldDueDateBetweenOrderByCreatedAtDesc(userId, first, last);

        Map<UUID, Task> tasksById = tasks.stream()
                .collect(Collectors.toMap(Task::getId, t -> t, (a, b) -> a));
        List<SummaryResponse.TopPostponedItem> topPostponed = logs.stream()
                .collect(Collectors.groupingBy(Postponement::getTaskId, LinkedHashMap::new,
                        Collectors.counting()))
                .entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .limit(5)
                .map(e -> {
                    Task original = tasksById.get(e.getKey());
                    return SummaryResponse.TopPostponedItem.builder()
                            .itemId(e.getKey())
                            .title(original != null ? original.getTitle() : null)
                            .times(e.getValue().intValue())
                            .build();
                })
                .toList();

        // one row per calendar day so chart code never has to fill gaps
        Map<LocalDate, int[]> byDay = new HashMap<>();   // [total, done]
        for (Task t : tasks) {
            int[] slot = byDay.computeIfAbsent(t.getDueDate(), d -> new int[2]);
            slot[0]++;
            if (t.getStatus() == TaskStatus.DONE) slot[1]++;
        }
        List<SummaryResponse.DailyCount> daily = new ArrayList<>();
        for (LocalDate d = first; !d.isAfter(last); d = d.plusDays(1)) {
            int[] slot = byDay.getOrDefault(d, new int[2]);
            daily.add(SummaryResponse.DailyCount.builder()
                    .date(d).total(slot[0]).done(slot[1]).build());
        }

        return SummaryResponse.builder()
                .month(ym.toString())
                .counts(counts)
                .completionRate(completionRate)
                .postponementCount(logs.size())
                .topPostponedItems(topPostponed)
                .daily(daily)
                .llmSummary(loadLlmParagraph(userId, ym, counts, completionRate, logs.size(), topPostponed))
                .build();
    }

    /**
     * Returns the cached paragraph when there is one, otherwise asks the model
     * once and stores the result. A failed call returns null and stores nothing,
     * so the numbers above are never held hostage by the LLM.
     */
    private String loadLlmParagraph(UUID userId, YearMonth ym,
                                    Map<String, Integer> counts,
                                    double completionRate,
                                    int postponementCount,
                                    List<SummaryResponse.TopPostponedItem> topPostponed) {
        String month = ym.toString();
        String cached = cacheRepository.findByUserIdAndMonth(userId, month)
                .map(SummaryLlmCache::getContent)
                .orElse(null);
        if (cached != null) {
            return cached;
        }

        StringBuilder input = new StringBuilder();
        input.append("Month: ").append(month).append('\n');
        counts.forEach((k, v) -> input.append(capitalize(k)).append(": ").append(v).append('\n'));
        input.append("Completion rate: ").append(String.format("%.0f%%", completionRate * 100)).append('\n');
        input.append("Postponement events: ").append(postponementCount).append('\n');
        if (!topPostponed.isEmpty()) {
            input.append("Most postponed items:\n");
            for (SummaryResponse.TopPostponedItem item : topPostponed) {
                input.append("- ").append(item.getTitle() == null ? "(deleted)" : item.getTitle())
                     .append(" (").append(item.getTimes()).append("x)\n");
            }
        }

        try {
            String text = chatClient.prompt()
                    .system("""
                            You write the closing paragraph of a monthly task report.
                            Reply with 2-3 sentences of plain prose only: no lists, no markdown,
                            no heading. Every claim must come from the NUMBERS given - never
                            invent tasks, dates or events. Mention the completion rate and, when
                            there were postponements, what kept moving. Keep an encouraging but
                            matter-of-fact tone.""")
                    .user(input.toString())
                    .call()
                    .content();
            if (text == null || text.isBlank()) {
                return null;
            }
            text = text.trim();
            SummaryLlmCache row = new SummaryLlmCache();
            row.setUserId(userId);
            row.setMonth(month);
            row.setContent(text);
            cacheRepository.save(row);
            return text;
        } catch (Exception e) {
            log.warn("monthly summary LLM call failed for {} {}: {}",
                    userId, month, e.getMessage());
            return null;
        }
    }

    private static String capitalize(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
