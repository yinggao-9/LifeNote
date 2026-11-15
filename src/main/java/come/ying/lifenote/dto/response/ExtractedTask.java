package come.ying.lifenote.dto.response;

import lombok.Data;

import java.util.List;

/**
 * One item of the JSON array the extraction prompt allows the model to return:
 * [
 *   { "title": "...", "dueDate": "2026-11-30", "dueTime": "15:00",
 *     "place": "...", "documentsToBring": ["..."], "notes": "...", "confidence": 0.9 }
 * ]
 * dueDate/dueTime stay null when unknown - the model must never invent a date.
 */
@Data
public class ExtractedTask {
    private String title;
    private String dueDate;          // yyyy-MM-dd or null
    private String dueTime;          // HH:mm or null
    private String place;
    private List<String> documentsToBring;
    private String notes;
    private Double confidence;
}
