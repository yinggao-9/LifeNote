package come.ying.lifenote.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalTime;

@Data
public class UserSettingsUpdateRequest {

    @NotNull(message = "is not a valid time zone")
    private String timeZone;

    @NotNull(message = "must be in HH:mm:ss format")
    @JsonFormat(pattern = "HH:mm:ss")
    private LocalTime eveningReminderTime;

    @NotNull(message = "must be true or false")
    private Boolean emailRemindersEnabled;

    @NotNull(message = "is not an available model")
    private String aiModel;
}
