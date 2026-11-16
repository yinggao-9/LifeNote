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
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Generation rules (1-day window, owner timezone, idempotency) and the bell
 * panel API. The email sender is mocked: these tests only care whether send
 * was invoked, never what a mail server would do.
 */
class ReminderServiceTest {

    private final ReminderRepository reminders = mock(ReminderRepository.class);
    private final TaskRepository tasks = mock(TaskRepository.class);
    private final UserSettingsRepository settings = mock(UserSettingsRepository.class);
    private final ReminderEmailService email = mock(ReminderEmailService.class);

    private final ReminderService service =
            new ReminderService(reminders, tasks, settings, email);

    private static Task dueTask(UUID userId, LocalDate due) {
        Task t = new Task();
        t.setUserId(userId);
        t.setDueDate(due);
        t.setTitle("task due " + due);
        t.setStatus(TaskStatus.TODO);
        return t;
    }

    private static UserSetting setting(UUID userId, String zone, LocalTime evening) {
        UserSetting s = new UserSetting();
        s.setId(userId);
        s.setTimeZone(zone);
        s.setEveningReminderTime(evening);
        return s;
    }

    private void stubCandidates(List<Task> rows) {
        LocalDate utcToday = LocalDate.now(ZoneId.of("UTC"));
        when(tasks.findByStatusAndDueDateBetween(
                eq(TaskStatus.TODO), eq(utcToday.minusDays(1)), eq(utcToday.plusDays(2))))
                .thenReturn(rows);
    }

    // ---------- deadline pass ----------

    @Test
    void taskDueTomorrowGetsExactlyOneDeadlineReminder() {
        UUID userId = UUID.randomUUID();
        Task tomorrow = dueTask(userId, LocalDate.now(ZoneId.of("UTC")).plusDays(1));
        stubCandidates(List.of(tomorrow));
        when(settings.findAllById(anySet())).thenReturn(List.of());
        when(reminders.existsByUserIdAndTaskIdAndRemindForDateAndType(
                any(UUID.class), any(UUID.class), any(LocalDate.class), eq(ReminderType.DEADLINE)))
                .thenReturn(false);

        service.generateDeadlineReminders();

        ArgumentCaptor<Reminder> captor = ArgumentCaptor.forClass(Reminder.class);
        verify(reminders, times(1)).save(captor.capture());
        Reminder saved = captor.getValue();
        assertEquals(ReminderType.DEADLINE, saved.getType());
        assertEquals(tomorrow.getDueDate(), saved.getRemindForDate());
        assertTrue(saved.getTitle().startsWith("Due tomorrow:"));
        assertFalse(saved.getRead());
        verify(email, times(1)).send(any(Reminder.class));
    }

    @Test
    void taskNotDueTomorrowIsSkipped() {
        UUID userId = UUID.randomUUID();
        Task dueToday = dueTask(userId, LocalDate.now(ZoneId.of("UTC")));
        stubCandidates(List.of(dueToday));
        when(settings.findAllById(anySet())).thenReturn(List.of());

        service.generateDeadlineReminders();

        verify(reminders, never()).save(any(Reminder.class));
        verify(email, never()).send(any(Reminder.class));
    }

    @Test
    void secondRunDoesNotDuplicateAReminder() {
        UUID userId = UUID.randomUUID();
        Task tomorrow = dueTask(userId, LocalDate.now(ZoneId.of("UTC")).plusDays(1));
        stubCandidates(List.of(tomorrow));
        when(settings.findAllById(anySet())).thenReturn(List.of());
        when(reminders.existsByUserIdAndTaskIdAndRemindForDateAndType(
                any(UUID.class), any(UUID.class), any(LocalDate.class), eq(ReminderType.DEADLINE)))
                .thenReturn(true);

        service.generateDeadlineReminders();

        verify(reminders, never()).save(any(Reminder.class));
        verify(email, never()).send(any(Reminder.class));
    }

    @Test
    void ownerTimezoneDecidesWhatTomorrowMeans() {
        // Auckland runs ahead of UTC: its "tomorrow" is a different calendar day
        UUID userId = UUID.randomUUID();
        LocalDate aucklandToday = LocalDate.now(ZoneId.of("Pacific/Auckland"));
        Task aucklandTomorrow = dueTask(userId, aucklandToday.plusDays(1));
        stubCandidates(List.of(aucklandTomorrow));
        when(settings.findAllById(anySet()))
                .thenReturn(List.of(setting(userId, "Pacific/Auckland", LocalTime.of(21, 0))));
        when(reminders.existsByUserIdAndTaskIdAndRemindForDateAndType(
                any(UUID.class), any(UUID.class), any(LocalDate.class), eq(ReminderType.DEADLINE)))
                .thenReturn(false);

        service.generateDeadlineReminders();

        verify(reminders, times(1)).save(any(Reminder.class));
    }

    // ---------- evening pass ----------

    @Test
    void eveningDigestFiresOnceAtTheConfiguredMinute() {
        UUID userId = UUID.randomUUID();
        LocalTime nowUtc = LocalTime.now(ZoneId.of("UTC")).withSecond(0).withNano(0);
        when(settings.findAll())
                .thenReturn(List.of(setting(userId, "UTC", nowUtc)));
        when(reminders.existsByUserIdAndRemindForDateAndType(
                any(UUID.class), any(LocalDate.class), eq(ReminderType.EVENING)))
                .thenReturn(false);
        Task open = dueTask(userId, LocalDate.now(ZoneId.of("UTC")));
        when(tasks.findByUserIdAndDueDateAndStatus(
                any(UUID.class), any(LocalDate.class), eq(TaskStatus.TODO)))
                .thenReturn(List.of(open));

        service.generateEveningReminders();

        ArgumentCaptor<Reminder> captor = ArgumentCaptor.forClass(Reminder.class);
        verify(reminders, times(1)).save(captor.capture());
        assertEquals(ReminderType.EVENING, captor.getValue().getType());
        assertTrue(captor.getValue().getBody().contains(open.getTitle()));
        verify(email, times(1)).send(any(Reminder.class));
    }

    @Test
    void eveningDigestSkipsUsersWhoseTimeHasNotCome() {
        UUID userId = UUID.randomUUID();
        LocalTime later = LocalTime.now(ZoneId.of("UTC")).plusHours(3).withSecond(0).withNano(0);
        when(settings.findAll())
                .thenReturn(List.of(setting(userId, "UTC", later)));

        service.generateEveningReminders();

        verify(reminders, never()).save(any(Reminder.class));
    }

    @Test
    void eveningDigestDoesNotRepeatOnTheSameDay() {
        UUID userId = UUID.randomUUID();
        LocalTime nowUtc = LocalTime.now(ZoneId.of("UTC")).withSecond(0).withNano(0);
        when(settings.findAll())
                .thenReturn(List.of(setting(userId, "UTC", nowUtc)));
        when(reminders.existsByUserIdAndRemindForDateAndType(
                any(UUID.class), any(LocalDate.class), eq(ReminderType.EVENING)))
                .thenReturn(true);

        service.generateEveningReminders();

        verify(reminders, never()).save(any(Reminder.class));
    }

    @Test
    void eveningBodyListsUpToTenOpenTasks() {
        UUID userId = UUID.randomUUID();
        LocalTime nowUtc = LocalTime.now(ZoneId.of("UTC")).withSecond(0).withNano(0);
        when(settings.findAll())
                .thenReturn(List.of(setting(userId, "UTC", nowUtc)));
        when(reminders.existsByUserIdAndRemindForDateAndType(
                any(UUID.class), any(LocalDate.class), eq(ReminderType.EVENING)))
                .thenReturn(false);
        List<Task> twelve = java.util.stream.IntStream.range(0, 12)
                .mapToObj(i -> dueTask(userId, LocalDate.now(ZoneId.of("UTC"))))
                .toList();
        when(tasks.findByUserIdAndDueDateAndStatus(
                any(UUID.class), any(LocalDate.class), eq(TaskStatus.TODO)))
                .thenReturn(twelve);

        service.generateEveningReminders();

        ArgumentCaptor<Reminder> captor = ArgumentCaptor.forClass(Reminder.class);
        verify(reminders).save(captor.capture());
        String body = captor.getValue().getBody();
        assertTrue(body.contains("...and 2 more"), body);
        assertEquals(10, body.lines().filter(l -> l.startsWith("- ")).count());
    }

    @Test
    void emptyEveningBodySaysNothingLeft() {
        UUID userId = UUID.randomUUID();
        LocalTime nowUtc = LocalTime.now(ZoneId.of("UTC")).withSecond(0).withNano(0);
        when(settings.findAll())
                .thenReturn(List.of(setting(userId, "UTC", nowUtc)));
        when(reminders.existsByUserIdAndRemindForDateAndType(
                any(UUID.class), any(LocalDate.class), eq(ReminderType.EVENING)))
                .thenReturn(false);
        when(tasks.findByUserIdAndDueDateAndStatus(
                any(UUID.class), any(LocalDate.class), eq(TaskStatus.TODO)))
                .thenReturn(List.of());

        service.generateEveningReminders();

        ArgumentCaptor<Reminder> captor = ArgumentCaptor.forClass(Reminder.class);
        verify(reminders).save(captor.capture());
        assertTrue(captor.getValue().getBody().contains("Nothing left open today"));
    }

    // ---------- bell panel API ----------

    @Test
    void markReadOnOwnReminderSetsReadFlag() {
        UUID userId = UUID.randomUUID();
        Reminder row = new Reminder();
        row.setId(UUID.randomUUID());
        row.setUserId(userId);
        row.setRead(false);
        when(reminders.findById(row.getId())).thenReturn(Optional.of(row));

        service.markRead(userId, row.getId());

        assertTrue(row.getRead());
        verify(reminders).save(row);
    }

    @Test
    void markReadOnSomeoneElsesReminderRaises1301() {
        Reminder row = new Reminder();
        row.setId(UUID.randomUUID());
        row.setUserId(UUID.randomUUID());   // a different owner
        when(reminders.findById(row.getId())).thenReturn(Optional.of(row));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.markRead(UUID.randomUUID(), row.getId()));

        assertEquals(1301, ex.getCode());
        verify(reminders, never()).save(any(Reminder.class));
    }

    @Test
    void markReadOnMissingReminderRaises1301() {
        when(reminders.findById(any(UUID.class))).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.markRead(UUID.randomUUID(), UUID.randomUUID()));

        assertEquals(1301, ex.getCode());
    }

    @Test
    void listMapsRowsAndPicksTheRightQuery() {
        UUID userId = UUID.randomUUID();
        Reminder row = new Reminder();
        row.setUserId(userId);
        row.setType(ReminderType.EVENING);
        row.setTitle("Due tomorrow: homework");
        when(reminders.findByUserIdAndReadFalseOrderByCreatedAtDesc(userId))
                .thenReturn(List.of(row));

        List<ReminderResponse> all = service.list(userId, true);

        assertEquals(1, all.size());
        verify(reminders).findByUserIdAndReadFalseOrderByCreatedAtDesc(userId);
        verify(reminders, never()).findByUserIdOrderByReadAscCreatedAtDesc(any(UUID.class));
        assertNotNull(all.get(0));
    }
}
