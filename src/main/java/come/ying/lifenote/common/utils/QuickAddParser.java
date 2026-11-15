package come.ying.lifenote.common.utils;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Channel 1 (quick-add) in one pure function: turn a typed sentence into
 * date / time / title without calling the LLM. Failing to find a date is a
 * normal branch (dateFound=false), not an error - the frontend then opens the
 * form so the user can supply one (proposal 5.1 "a missing date is asked for").
 *
 * English date and time expressions only:
 *   absolute  2026-11-30 / 2026/11/30 / 30/11 / Nov 30 / November 30, 2026
 *   relative  today / tomorrow / the day after tomorrow / Wednesday / this Friday / next Monday
 *   time      15:00 / 3:00pm / 8:30 am / 3 pm
 */
public class QuickAddParser {

    public record Parsed(String title, LocalDate dueDate, LocalTime dueTime, boolean dateFound) {}

    public static Parsed parse(String input, LocalDate today) {
        String text = input == null ? "" : input.trim();
        if (text.isEmpty()) {
            return new Parsed("", null, null, false);
        }

        // time first: it is often embedded in a longer phrase
        LocalTime time = extractTime(text);
        String remain = time != null ? removeMatchedTime(text) : text;

        LocalDate date = extractDate(remain, today);
        if (date != null) {
            remain = removeMatchedDate(remain);
        }

        String title = remain.replaceAll("\\s+", " ")
                             .replaceAll("^[,;:.]+|[,;:.]+$", "")
                             .trim();
        return new Parsed(title, date, time, date != null);
    }

    // ================= date =================

    private static LocalDate extractDate(String s, LocalDate today) {
        // 2026-11-30 or 2026/11/30
        Matcher m = Pattern.compile("(\\d{4})[-/](\\d{1,2})[-/](\\d{1,2})").matcher(s);
        if (m.find()) return safeDate(m.group(1), m.group(2), m.group(3));

        // 30/11 or 30/11/2026 (day first); only unambiguous when day > 12
        m = Pattern.compile("(\\d{1,2})/(\\d{1,2})(?:/(\\d{4}))?").matcher(s);
        if (m.find()) {
            int d = Integer.parseInt(m.group(1)), mo = Integer.parseInt(m.group(2));
            if (d > 12) {
                int y = m.group(3) != null ? Integer.parseInt(m.group(3)) : today.getYear();
                return safeDate(String.valueOf(y), String.valueOf(mo), String.valueOf(d));
            }
        }

        // Nov 30 / November 30, 2026 (English month first)
        m = Pattern.compile("(?i)(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*\\.?\\s+(\\d{1,2})(?:,?\\s*(\\d{4}))?").matcher(s);
        if (m.find()) {
            int mo = monthNumber(m.group(1));
            int y = m.group(3) != null ? Integer.parseInt(m.group(3)) : today.getYear();
            return safeDate(String.valueOf(y), String.valueOf(mo), m.group(2));
        }

        // relative: today / tomorrow / day after tomorrow
        // (the longest phrase must be tested first: it contains "tomorrow")
        if (matchesAny(s, "today", "tonight")) return today;
        if (matchesAny(s, "the day after tomorrow")) return today.plusDays(2);
        if (matchesAny(s, "tomorrow")) return today.plusDays(1);

        // weekday names: the next such day, today included;
        // "next Monday" must be strictly in the future, so start from tomorrow
        Integer wd = weekdayFromText(s);
        if (wd != null) {
            LocalDate base = matchesAny(s, "next") ? today.plusDays(1) : today;
            return base.with(TemporalAdjusters.nextOrSame(DayOfWeek.of(wd)));
        }
        return null;
    }

    private static Integer weekdayFromText(String s) {
        String[][] table = {
            {"monday","mon"}, {"tuesday","tue","tues"}, {"wednesday","wed"},
            {"thursday","thu","thur"}, {"friday","fri"}, {"saturday","sat"},
            {"sunday","sun"}
        };
        String low = s.toLowerCase();
        for (int i = 0; i < table.length; i++) {
            for (String k : table[i]) {
                if (low.contains(k)) return i + 1;
            }
        }
        return null;
    }

    /** Illegal dates (Feb 30) become null, i.e. "not parsed", and the user fills the form. */
    private static LocalDate safeDate(String y, String m, String d) {
        try {
            return LocalDate.of(Integer.parseInt(y), Integer.parseInt(m), Integer.parseInt(d));
        } catch (Exception e) {
            return null;
        }
    }

    private static int monthNumber(String s) {
        String[] names = {"jan","feb","mar","apr","may","jun",
                          "jul","aug","sep","oct","nov","dec"};
        for (int i = 0; i < names.length; i++) {
            if (s.toLowerCase().startsWith(names[i])) return i + 1;
        }
        return 0;
    }

    private static boolean matchesAny(String s, String... keys) {
        String low = s.toLowerCase();
        for (String k : keys) if (low.contains(k.toLowerCase())) return true;
        return false;
    }

    // ================= time =================

    private static LocalTime extractTime(String s) {
        // 15:00 / 3:00
        Matcher m = Pattern.compile("(\\d{1,2}):(\\d{2})").matcher(s);
        if (m.find()) {
            LocalTime t = tryTime(m.group(1), m.group(2));
            if (t != null) return adjustAmPm(t, s, m.end());
        }
        // 3pm / 8:30 am / 3 pm
        m = Pattern.compile("(?i)(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)").matcher(s);
        if (m.find()) {
            int h = Integer.parseInt(m.group(1)) % 12;
            if (m.group(3).equalsIgnoreCase("pm")) h += 12;
            return LocalTime.of(h, m.group(2) == null ? 0 : Integer.parseInt(m.group(2)));
        }
        return null;
    }

    /** "3:00" without am/pm reads as afternoon; 12am rolls back to 00:00. */
    private static LocalTime adjustAmPm(LocalTime t, String s, int afterPos) {
        String tail = s.substring(afterPos).toLowerCase();
        if (tail.contains("pm") && t.getHour() < 12) return t.plusHours(12);
        if (tail.contains("am") && t.getHour() == 12) return t.minusHours(12);
        if (!tail.contains("am") && !tail.contains("pm") && t.getHour() < 8) return t.plusHours(12);
        return t;
    }

    private static LocalTime tryTime(String h, String m) {
        try {
            int hh = Integer.parseInt(h), mm = Integer.parseInt(m);
            if (hh > 23 || mm > 59) return null;
            return LocalTime.of(hh, mm);
        } catch (Exception e) {
            return null;
        }
    }

    // ================= strip recognised fragments from the remaining title =================

    private static String removeMatchedTime(String s) {
        String out = s.replaceAll("(?i)\\d{1,2}:\\d{2}\\s*(am|pm)?", " ");
        return out.replaceAll("(?i)\\d{1,2}\\s*(am|pm)\\b", " ");
    }

    private static String removeMatchedDate(String s) {
        String out = s.replaceAll("\\d{4}[-/]\\d{1,2}[-/]\\d{1,2}", " ");
        out = out.replaceAll("\\b\\d{1,2}/\\d{1,2}(?:/\\d{4})?\\b", " ");
        out = out.replaceAll("(?i)(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*\\.?\\s+\\d{1,2}(,?\\s*\\d{4})?", " ");
        out = out.replaceAll("(?i)\\b(today|tonight|tomorrow|the day after tomorrow)\\b", " ");
        out = out.replaceAll("(?i)\\bnext\\s+", " ");
        out = out.replaceAll("(?i)\\b(monday|tuesday|wednesday|thursday|friday|saturday|sunday|mon|tue|wed|thu|fri|sat|sun)\\b", " ");
        return out;
    }

    // ================= self-test (run this class's main to check the rules) =================
    public static void main(String[] args) {
        LocalDate today = LocalDate.of(2026, 11, 14);
        String[] cases = {
                "Submit the form by 2026-12-01",
                "I will pick up my child tomorrow at 3 pm",
                "Nov 30 meeting with Bob",
                "buy milk",                       // expect dateFound=false
                "Wednesday hand in the report",
                "call the bank at 15:30",
                "next Monday flight at 9am",
                "the day after tomorrow dentist"  // must be +2 days, not tomorrow
        };
        for (String c : cases) {
            Parsed p = parse(c, today);
            System.out.printf("%-45s -> date=%s time=%s title='%s'%n",
                    c, p.dueDate(), p.dueTime(), p.title());
        }
    }
}
