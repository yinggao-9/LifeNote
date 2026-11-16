package come.ying.lifenote.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Two timers, both idempotent so a restart or a slow run can never
 * duplicate a reminder:
 * - deadlines: hourly, judged against each owner's local date
 * - evening digest: every minute, fires at the user's configured time
 */
@Slf4j
@Component
public class ReminderScheduler {

    private final ReminderService reminderService;

    public ReminderScheduler(ReminderService reminderService) {
        this.reminderService = reminderService;
    }

    @Scheduled(initialDelayString = "PT30S", fixedDelayString = "PT1H")
    public void scanDeadlines() {
        try {
            reminderService.generateDeadlineReminders();
        } catch (Exception e) {
            log.error("deadline reminder scan failed: {}", e.getMessage(), e);
        }
    }

    @Scheduled(initialDelayString = "PT5S", fixedDelayString = "PT1M")
    public void scanEveningDigest() {
        try {
            reminderService.generateEveningReminders();
        } catch (Exception e) {
            log.error("evening reminder scan failed: {}", e.getMessage(), e);
        }
    }
}
