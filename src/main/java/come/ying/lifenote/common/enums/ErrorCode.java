package come.ying.lifenote.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ErrorCode {
    PASSWORD_ERROR(1001, "Invalid email or password."),
    USER_NOT_FOUND(1002, "Email is not registered."),
    USER_SETTINGS_NOT_FOUND(1003, "User settings not found."),
    ACCOUNT_LOCKED(1004, "Too many attempts. Try again later."),
    SYSTEM_ERROR(1005, "An unexpected error occurred."),
    EMAIL_ALREADY_REGISTERED(1006, "This email is already registered."),
    INVALID_TIME_ZONE(1007, "Invalid time zone format. Please use a standard time zone such as Asia/Shanghai"),
    INVALID_AI_MODEL(1008, "is not an available model");
    private final Integer code;
    private final String msg;
}
