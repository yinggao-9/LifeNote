package come.ying.lifenote.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
@Builder
public class ConfirmResponse {
    private List<UUID> taskIds;    // ids of the tasks created, for front-end navigation
    private String message;
}
