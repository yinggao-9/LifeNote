package come.ying.lifenote.service;

import come.ying.lifenote.entity.Task;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the .ics builder (endpoint 19's engine): all-day events
 * get the spec's exclusive end date, timed events default to one hour, and
 * every event carries a stable UID so re-imports update instead of
 * duplicating. Pure function - no database, no Spring.
 */
class IcsExportServiceTest {

    private final IcsExportService service = new IcsExportService();

    private static Task task(String title, LocalDate date, LocalTime time, String notes) {
        Task t = new Task();
        t.setUserId(java.util.UUID.randomUUID());
        t.setTitle(title);
        t.setDueDate(date);
        t.setDueTime(time);
        t.setNotes(notes);
        return t;
    }

    @Test
    void allDayEventEndsExclusivelyTheNextDay() throws Exception {
        Task t = task("Pay rent", LocalDate.of(2026, 12, 16), null, null);

        String ics = new String(service.build(List.of(t)));

        assertTrue(ics.contains("BEGIN:VCALENDAR"), ics);
        assertTrue(ics.contains("SUMMARY:Pay rent"), ics);
        assertTrue(ics.contains("UID:" + t.getId() + "@lifenote"), ics);
        // DTEND for DATE values is exclusive per RFC 5545: the day after the due date
        assertTrue(ics.contains("20261216"), ics);
        assertTrue(ics.contains("20261217"), ics);
        assertTrue(!ics.contains("20261216T"), "all-day events must not carry a time: " + ics);
    }

    @Test
    void timedEventDefaultsToOneHour() throws Exception {
        Task t = task("Dentist", LocalDate.of(2026, 12, 15), LocalTime.of(10, 30), null);

        String ics = new String(service.build(List.of(t)));

        assertTrue(ics.contains("20261215T103000"), ics);
        assertTrue(ics.contains("20261215T113000"), ics);
    }

    @Test
    void notesBecomeTheDescription() throws Exception {
        Task t = task("Interview", LocalDate.of(2026, 12, 20), LocalTime.of(9, 0),
                "bring passport and portfolio");

        String ics = new String(service.build(List.of(t)));

        assertTrue(ics.contains("DESCRIPTION:bring passport and portfolio"), ics);
    }

    @Test
    void everyTaskBecomesOneEvent() throws Exception {
        String ics = new String(service.build(List.of(
                task("one", LocalDate.of(2026, 12, 1), null, null),
                task("two", LocalDate.of(2026, 12, 2), LocalTime.of(8, 0), null))));

        long events = ics.lines().filter(l -> l.equals("BEGIN:VEVENT")).count();
        assertTrue(events == 2, ics);
    }

    @Test
    void emptyListStillProducesAValidCalendar() throws Exception {
        String ics = new String(service.build(List.of()));

        assertTrue(ics.contains("BEGIN:VCALENDAR"), ics);
        assertTrue(ics.contains("END:VCALENDAR"), ics);
    }
}
