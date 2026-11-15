package come.ying.lifenote.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

/** One "source material" entry on the task detail page: the original notice/file behind this task. */
@Data
@Builder
public class SourceResponse {
    private UUID documentId;
    private String filename;      // original file name; pasted-text sources have none
    private String contentType;
    private String url;           // 10-minute presigned URL, signed on demand, never stored
}
