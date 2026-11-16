package come.ying.lifenote.common.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Quick-add is a pure function, so every rule gets a direct case here.
 * The fixed "today" is Saturday 2026-11-14 so weekday maths is predictable.
 */
class QuickAddParserTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 14);

    static Stream<Arguments> cases() {
        return Stream.of(
                // ---- absolute dates ----
                Arguments.of("Submit the form by 2026-12-01", "Submit the form", "2026-12-01", null),
                Arguments.of("report due 2026/11/30", "report due", "2026-11-30", null),
                Arguments.of("Submit form 30/11/2026", "Submit form", "2026-11-30", null),
                Arguments.of("Nov 30 meeting with Bob", "meeting with Bob", "2026-11-30", null),
                Arguments.of("party November 30, 2026", "party", "2026-11-30", null),
                // day <= 12 is ambiguous (11/12 could be Nov 12 or Dec 11) - not parsed
                Arguments.of("pay 11/12 bill", "pay 11/12 bill", null, null),
                // illegal calendar date must not crash and must not invent a day
                Arguments.of("party Feb 30", "party Feb 30", null, null),

                // ---- relative dates (English) ----
                Arguments.of("today gym", "gym", "2026-11-14", null),
                Arguments.of("the day after tomorrow dentist", "dentist", "2026-11-16", null),
                Arguments.of("I will pick up my child tomorrow at 3 pm", "I will pick up my child", "2026-11-15", "15:00"),
                Arguments.of("buy milk tomorrow at 10am", "buy milk", "2026-11-15", "10:00"),

                // ---- weekday names ----
                Arguments.of("Wednesday hand in the report", "hand in the report", "2026-11-18", null),
                // today is Saturday, so "Saturday" means today (nextOrSame)
                Arguments.of("Saturday laundry", "laundry", "2026-11-14", null),
                Arguments.of("next Monday flight at 9am", "flight", "2026-11-16", "09:00"),

                // ---- Chinese dates ----
                Arguments.of("周五 18:00 和导师开会", "和导师开会", "2026-11-20", "18:00"),
                Arguments.of("明天交作业", "交作业", "2026-11-15", null),
                Arguments.of("后天取快递", "取快递", "2026-11-16", null),
                Arguments.of("今天开会", "开会", "2026-11-14", null),
                Arguments.of("下周三小组讨论", "小组讨论", "2026-11-18", null),
                Arguments.of("星期一交房租", "交房租", "2026-11-16", null),
                Arguments.of("周六和同学打球", "和同学打球", "2026-11-14", null),

                // ---- times ----
                Arguments.of("call the bank at 15:30", "call the bank", null, "15:30"),
                Arguments.of("dinner at 7:30", "dinner", null, "19:30"),   // no am/pm and hour < 8 reads as evening
                Arguments.of("flight 12am", "flight", null, "00:00"),
                Arguments.of("8:30 am standup", "standup", null, "08:30"),

                // ---- no date at all: the normal NEED_INPUT branch ----
                Arguments.of("buy milk", "buy milk", null, null),
                // "at" must survive when no time follows it
                Arguments.of("meet at the clinic", "meet at the clinic", null, null)
        );
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("cases")
    void parse(String input, String expectedTitle, String expectedDate, String expectedTime) {
        QuickAddParser.Parsed p = QuickAddParser.parse(input, TODAY);

        assertEquals(expectedTitle, p.title(), "title of: " + input);
        assertEquals(expectedDate, p.dueDate() == null ? null : p.dueDate().toString(),
                "date of: " + input);
        assertEquals(expectedTime,
                p.dueTime() == null ? null : String.format("%02d:%02d", p.dueTime().getHour(), p.dueTime().getMinute()),
                "time of: " + input);
        assertEquals(expectedDate != null, p.dateFound(), "dateFound of: " + input);
    }

    @Test
    void nullAndBlankInputAreNeedInput() {
        for (String input : new String[]{null, "", "   "}) {
            QuickAddParser.Parsed p = QuickAddParser.parse(input, TODAY);
            assertEquals("", p.title());
            assertNull(p.dueDate());
            assertNull(p.dueTime());
            assertFalse(p.dateFound());
        }
    }

    @Test
    void dateFoundIsTrueExactlyWhenDueDateIsPresent() {
        assertTrue(QuickAddParser.parse("tomorrow task", TODAY).dateFound());
        assertFalse(QuickAddParser.parse("task without date", TODAY).dateFound());
    }
}
