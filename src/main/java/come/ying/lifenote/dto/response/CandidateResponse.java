package come.ying.lifenote.dto.response;

import come.ying.lifenote.entity.TaskCandidate;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/** Public shape of one extraction draft, shared by list/detail/submit responses. */
@Data
@Builder
public class CandidateResponse {
    private UUID id;
    private String title;
    private LocalDate dueDate;
    private LocalTime dueTime;
    private String place;
    private List<String> documentsToBring;
    private String notes;
    private Double confidence;
    private String status;

    public static CandidateResponse from(TaskCandidate c) {
        return CandidateResponse.builder()
                .id(c.getId()).title(c.getTitle())
                .dueDate(c.getDueDate()).dueTime(c.getDueTime())
                .place(c.getPlace())
                .documentsToBring(c.getDocumentsToBring())
                .notes(c.getNotes()).confidence(c.getConfidence())
                .status(c.getStatus())
                .build();
    }
}
