package come.ying.lifenote.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class QuickAddRequest {
    @NotBlank(message = "text must not be blank")
    @Size(max = 500, message = "text must be shorter than 500 characters")
    private String text;
}
