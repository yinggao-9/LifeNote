package come.ying.lifenote.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Monthly summary, all figures keyed off due dates of that month so they
 * match what the calendar shows. llmSummary stays null until the LLM text
 * generation is wired up - the numbers must never depend on it.
 */
@Data
@Builder
public class SummaryResponse {
    private String month;                          // "2026-11"
    private Map<String, Integer> counts;           // total / done / cancelled / postponed / todo
    private Double completionRate;                 // done / total, 0 when total is 0
    private Integer postponementCount;             // log rows whose original due date is in the month
    private List<TopPostponedItem> topPostponedItems;
    private List<DailyCount> daily;                // one entry per calendar day, for charts
    private String llmSummary;                     // reserved, currently always null

    @Data
    @Builder
    public static class TopPostponedItem {
        private java.util.UUID itemId;             // the original task that kept moving
        private String title;
        private Integer times;
    }

    @Data
    @Builder
    public static class DailyCount {
        private LocalDate date;
        private Integer total;
        private Integer done;
    }
}
