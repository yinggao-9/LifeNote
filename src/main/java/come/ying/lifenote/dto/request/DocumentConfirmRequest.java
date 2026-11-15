package come.ying.lifenote.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class DocumentConfirmRequest {
    /** The confirm dialog submits the whole (possibly edited) draft set back. */
    @NotNull
    private List<CandidateUpdateRequest> candidates;
}
