package come.ying.lifenote.controller;

import come.ying.lifenote.common.ApiResponse.ApiResponse;
import come.ying.lifenote.common.utils.BaseContext;
import come.ying.lifenote.dto.response.SummaryResponse;
import come.ying.lifenote.service.SummaryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Monthly summary: counts, completion rate, postpone stats and the daily series. */
@Slf4j
@RestController
public class SummaryController {

    private final SummaryService summaryService;

    public SummaryController(SummaryService summaryService) {
        this.summaryService = summaryService;
    }

    @GetMapping("/api/summary")
    public ApiResponse<SummaryResponse> summary(@RequestParam String month) {
        UUID userId = BaseContext.getCurrentId();
        log.info("summary, userId={}, month={}", userId, month);
        return ApiResponse.success(summaryService.summary(userId, month));
    }
}
