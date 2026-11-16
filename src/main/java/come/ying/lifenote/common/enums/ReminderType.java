package come.ying.lifenote.common.enums;

/** Where a reminder row came from; both kinds are created only by the scheduler. */
public enum ReminderType {
    /** One day before a task's due date, while the task is still TODO. */
    DEADLINE,
    /** Daily digest of everything still open on that date. */
    EVENING
}
