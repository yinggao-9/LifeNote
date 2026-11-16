package come.ying.lifenote.controller;

import come.ying.lifenote.common.ApiResponse.ApiResponse;
import come.ying.lifenote.common.utils.BaseContext;
import come.ying.lifenote.dto.response.ReminderResponse;
import come.ying.lifenote.service.ReminderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** The bell panel: everything the scheduler decided the user should see. */
@Slf4j
@RestController
public class ReminderController {

    private final ReminderService reminderService;

    public ReminderController(ReminderService reminderService) {
        this.reminderService = reminderService;
    }

    @GetMapping("/api/reminders")
    public ApiResponse<List<ReminderResponse>> list(
            @RequestParam(name = "unreadOnly", defaultValue = "false") boolean unreadOnly) {
        UUID userId = BaseContext.getCurrentId();
        log.info("list reminders, userId={}, unreadOnly={}", userId, unreadOnly);
        return ApiResponse.success(reminderService.list(userId, unreadOnly));
    }

    @PostMapping("/api/reminders/{id}/read")
    public ApiResponse<Void> markRead(@PathVariable("id") UUID id) {
        UUID userId = BaseContext.getCurrentId();
        log.info("mark reminder read, userId={}, reminderId={}", userId, id);
        reminderService.markRead(userId, id);
        return ApiResponse.success();
    }
}
