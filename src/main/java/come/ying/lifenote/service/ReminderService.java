package come.ying.lifenote.service;

import come.ying.lifenote.common.enums.ReminderType;
import come.ying.lifenote.common.enums.TaskStatus;
import come.ying.lifenote.dto.response.ReminderResponse;
import come.ying.lifenote.entity.Reminder;
import come.ying.lifenote.entity.Task;
import come.ying.lifenote.entity.UserSetting;
import come.ying.lifenote.exception.BusinessException;
import come.ying.lifenote.repository.ReminderRepository;
import come.ying.lifenote.repository.TaskRepository;
import come.ying.lifenote.repository.UserSettingsRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Creates reminders (called by the scheduler) and serves the bell panel API.
 * Generation is idempotent: the exists-check plus the partial unique indexes
 * in V3 guarantee one row per task/day no matter how often a job runs.
 */
@Slf4j
@Service
public class ReminderService {

    private static final int EVENING_LIST_CAP = 10;

    private final ReminderRepository reminderRepository;
    private final TaskRepository taskRepository;
    private final UserSettingsRepository settingsRepository;
    private final ReminderEmailService emailService;

    public ReminderService(ReminderRepository reminderRepository,
                           TaskRepository taskRepository,
                           UserSettingsRepository settingsRepository,
                           ReminderEmailService emailService) {
        this.reminderRepository = reminderRepository;
        this.taskRepository = taskRepository;
        this.settingsRepository = settingsRepository;
        this.emailService = emailService;
    }

    // ---------- API ----------

    public List<ReminderResponse> list(UUID userId, boolean unreadOnly) {
        List<Reminder> rows = unreadOnly
                ? reminderRepository.findByUserIdAndReadFalseOrderByCreatedAtDesc(userId)
                : reminderRepository.findByUserIdOrderByReadAscCreatedAtDesc(userId);
        return rows.stream().map(ReminderResponse::from).toList();
    }

    /** Marks one reminder read; a miss means "not yours or not there", both answer 1301. */
    public void markRead(UUID userId, UUID reminderId) {
        Reminder reminder = reminderRepository.findById(reminderId)
                .filter(r -> userId.equals(r.getUserId()))
                .orElseThrow(() -> new BusinessException(1301, "Reminder not found."));
        reminder.setRead(true);
        reminderRepository.save(reminder);
    }

    // ---------- scheduler entry points ----------

    /**
     * Deadline pass: every TODO task that is "tomorrow" in its owner's time zone
     * gets one DEADLINE reminder. The candidate window is wider than one day
     * because users sit in different zones around the clock.
     */
    public void generateDeadlineReminders() {
        LocalDate utcToday = LocalDate.now(ZoneId.of("UTC"));
        List<Task> candidates = taskRepository.findByStatusAndDueDateBetween(
                TaskStatus.TODO, utcToday.minusDays(1), utcToday.plusDays(2));
        if (candidates.isEmpty()) {
            return;
        }
        Map<UUID, UserSetting> settings = settingsFor(candidates.stream()
                .map(Task::getUserId).collect(Collectors.toSet()));

        int created = 0;
        for (Task task : candidates) {
            UserSetting setting = settings.get(task.getUserId());
            LocalDate localToday = LocalDate.now(zoneOf(setting));
            if (!task.getDueDate().equals(localToday.plusDays(1))) {
                continue;
            }
            if (reminderRepository.existsByUserIdAndTaskIdAndRemindForDateAndType(
                    task.getUserId(), task.getId(), task.getDueDate(), ReminderType.DEADLINE)) {
                continue;
            }
            Reminder reminder = new Reminder();
            reminder.setUserId(task.getUserId());
            reminder.setTaskId(task.getId());
            reminder.setType(ReminderType.DEADLINE);
            reminder.setRemindForDate(task.getDueDate());
            reminder.setTitle(truncate("Due tomorrow: " + task.getTitle(), 200));
            reminder.setBody(deadlineBody(task));
            reminder.setRead(false);
            reminderRepository.save(reminder);
            emailService.send(reminder);
            created++;
        }
        if (created > 0) {
            log.info("created {} deadline reminder(s)", created);
        }
    }

    /**
     * Evening pass: runs every minute and fires for a user once the clock in
     * their zone hits their configured eveningReminderTime.
     */
    public void generateEveningReminders() {
        for (UserSetting setting : settingsRepository.findAll()) {
            LocalTime now = LocalTime.now(zoneOf(setting)).withSecond(0).withNano(0);
            if (!now.equals(setting.getEveningReminderTime().withSecond(0).withNano(0))) {
                continue;
            }
            LocalDate localToday = LocalDate.now(zoneOf(setting));
            UUID userId = setting.getId();
            if (reminderRepository.existsByUserIdAndRemindForDateAndType(
                    userId, localToday, ReminderType.EVENING)) {
                continue;
            }
            List<Task> open = taskRepository.findByUserIdAndDueDateAndStatus(
                    userId, localToday, TaskStatus.TODO);

            Reminder reminder = new Reminder();
            reminder.setUserId(userId);
            reminder.setTaskId(null);
            reminder.setType(ReminderType.EVENING);
            reminder.setRemindForDate(localToday);
            reminder.setTitle(truncate("Evening check-in - " + localToday, 200));
            reminder.setBody(eveningBody(open));
            reminder.setRead(false);
            reminderRepository.save(reminder);
            emailService.send(reminder);
            log.info("created evening reminder for {}", userId);
        }
    }

    // ---------- helpers ----------

    private static String deadlineBody(Task task) {
        StringBuilder body = new StringBuilder();
        body.append("Due: ").append(task.getDueDate());
        if (task.getDueTime() != null) {
            body.append(' ').append(task.getDueTime());
        }
        body.append('\n');
        if (task.getPlace() != null && !task.getPlace().isBlank()) {
            body.append("Place: ").append(task.getPlace()).append('\n');
        }
        if (task.getDocumentsToBring() != null && !task.getDocumentsToBring().isEmpty()) {
            body.append("Bring: ").append(String.join(", ", task.getDocumentsToBring())).append('\n');
        }
        if (task.getNotes() != null && !task.getNotes().isBlank()) {
            body.append(task.getNotes());
        }
        return body.toString().stripTrailing();
    }

    private static String eveningBody(List<Task> open) {
        if (open.isEmpty()) {
            return "Nothing left open today. Nice work.";
        }
        StringBuilder body = new StringBuilder("Still open today:");
        List<Task> shown = open.subList(0, Math.min(open.size(), EVENING_LIST_CAP));
        for (Task task : shown) {
            body.append("\n- ").append(task.getTitle());
        }
        if (open.size() > shown.size()) {
            body.append("\n...and ").append(open.size() - shown.size()).append(" more");
        }
        return body.toString();
    }

    private Map<UUID, UserSetting> settingsFor(Set<UUID> userIds) {
        return settingsRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(UserSetting::getId, Function.identity()));
    }

    private static ZoneId zoneOf(UserSetting setting) {
        if (setting == null) {
            return ZoneId.of("UTC");
        }
        try {
            return ZoneId.of(setting.getTimeZone());
        } catch (DateTimeException e) {
            // settings are validated on write, but a bad row must not kill the job
            return ZoneId.of("UTC");
        }
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 3) + "...";
    }
}
