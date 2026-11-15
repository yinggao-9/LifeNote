package come.ying.lifenote.service;

import come.ying.lifenote.entity.Task;
import net.fortuna.ical4j.data.CalendarOutputter;
import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.model.property.DtEnd;
import net.fortuna.ical4j.model.property.DtStart;
import net.fortuna.ical4j.model.property.Description;
import net.fortuna.ical4j.model.property.ProdId;
import net.fortuna.ical4j.model.property.Summary;
import net.fortuna.ical4j.model.property.Uid;
import net.fortuna.ical4j.model.property.Version;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Builds a .ics (iCalendar) file from tasks for direct import into Google
 * Calendar / Outlook / Apple Calendar. ical4j handles escaping (commas,
 * semicolons and newlines in titles), 75-char line folding and the date
 * formats (20261130 vs 2026-11-30); hand-rolling those is not worth it.
 */
@Service
public class IcsExportService {

    public byte[] build(List<Task> tasks) throws IOException {
        Calendar calendar = new Calendar();
        calendar.add(new ProdId("-//LifeNote//Task Export//EN"));
        Version version = new Version();
        version.setValue(Version.VALUE_2_0);   // outputs VERSION:2.0
        // deliberately not new Version(null, VALUE_2_0): those overloads make a
        // null first argument ambiguous and the code stops compiling
        calendar.add(version);

        for (Task task : tasks) {
            VEvent event = new VEvent();

            // every event needs a globally unique id so re-imports update
            // instead of duplicating
            event.add(new Uid(task.getId() + "@lifenote"));

            if (task.getDueTime() == null) {
                // all-day event: DTEND for DATE values is exclusive per the spec, so +1 day
                event.add(new DtStart<>(task.getDueDate()));
                event.add(new DtEnd<>(task.getDueDate().plusDays(1)));
            } else {
                // timed event: no end time in our model, default to one hour
                LocalDateTime start = task.getDueDate().atTime(task.getDueTime());
                event.add(new DtStart<>(start));
                event.add(new DtEnd<>(start.plusHours(1)));
            }

            event.add(new Summary(task.getTitle()));
            if (task.getNotes() != null && !task.getNotes().isBlank()) {
                event.add(new Description(task.getNotes()));
            }
            calendar.add(event);
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        // false = skip strict validation: one odd row must not fail the whole
        // export with a ValidationException - half a file beats no file
        new CalendarOutputter(false).output(calendar, out);
        return out.toByteArray();
    }
}
