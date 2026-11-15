package come.ying.lifenote.controller;

import come.ying.lifenote.common.ApiResponse.ApiResponse;
import come.ying.lifenote.common.utils.BaseContext;
import come.ying.lifenote.dto.request.CandidateUpdateRequest;
import come.ying.lifenote.dto.request.DocumentConfirmRequest;
import come.ying.lifenote.dto.response.CandidateResponse;
import come.ying.lifenote.dto.response.ConfirmResponse;
import come.ying.lifenote.dto.response.DocumentResponse;
import come.ying.lifenote.dto.response.DocumentSubmitResponse;
import come.ying.lifenote.service.DocumentService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

/** Material intake: submit, review drafts, confirm/cancel, retry, delete. */
@Slf4j
@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    /** Submit text or a file; extraction runs synchronously. */
    @PostMapping
    public ApiResponse<DocumentSubmitResponse> submit(
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "text", required = false) String text) {
        UUID userId = BaseContext.getCurrentId();
        log.info("submit document, userId={}, hasFile={}, textLen={}",
                 userId, file != null && !file.isEmpty(), text == null ? 0 : text.length());
        return ApiResponse.success(documentService.submit(userId, file, text));
    }

    /** My submission history. */
    @GetMapping
    public ApiResponse<List<DocumentResponse>> list() {
        return ApiResponse.success(documentService.list(BaseContext.getCurrentId()));
    }

    /** Detail view: 10-minute temporary source URL plus the drafts. */
    @GetMapping("/{id}")
    public ApiResponse<DocumentResponse> detail(@PathVariable UUID id) {
        return ApiResponse.success(documentService.detail(BaseContext.getCurrentId(), id));
    }

    /** Draft list. */
    @GetMapping("/{id}/candidates")
    public ApiResponse<List<CandidateResponse>> candidates(@PathVariable UUID id) {
        return ApiResponse.success(documentService.candidates(BaseContext.getCurrentId(), id));
    }

    /** Edit a single draft. */
    @PutMapping("/{id}/candidates/{candidateId}")
    public ApiResponse<CandidateResponse> updateCandidate(
            @PathVariable UUID id,
            @PathVariable UUID candidateId,
            @Valid @RequestBody CandidateUpdateRequest request) {
        return ApiResponse.success(
                documentService.updateCandidate(BaseContext.getCurrentId(), id, candidateId, request));
    }

    /** Bulk confirm: turn every draft into a real task. */
    @PostMapping("/{id}/confirm")
    public ApiResponse<ConfirmResponse> confirm(
            @PathVariable UUID id,
            @Valid @RequestBody DocumentConfirmRequest request) {
        return ApiResponse.success(
                documentService.confirm(BaseContext.getCurrentId(), id, request));
    }

    /** Cancel: discard every draft, save nothing. */
    @PostMapping("/{id}/cancel")
    public ApiResponse<String> cancel(@PathVariable UUID id) {
        documentService.cancel(BaseContext.getCurrentId(), id);
        return ApiResponse.success("Cancelled, nothing was saved.");
    }

    /** Retry extraction after an AI failure (same material). */
    @PostMapping("/{id}/reextract")
    public ApiResponse<DocumentSubmitResponse> reextract(@PathVariable UUID id) {
        return ApiResponse.success(documentService.reextract(BaseContext.getCurrentId(), id));
    }

    /** Delete the source (S3 object + drafts + audit rows); created tasks are kept. */
    @DeleteMapping("/{id}")
    public ApiResponse<String> delete(@PathVariable UUID id) {
        documentService.delete(BaseContext.getCurrentId(), id);
        return ApiResponse.success("Document deleted.");
    }
}
