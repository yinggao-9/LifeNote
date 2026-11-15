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

/** Endpoints 1-9: submit material, review drafts, confirm/cancel, retry, delete. */
@Slf4j
@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    /** Endpoint 1: submit text or a file, extraction runs synchronously. */
    @PostMapping
    public ApiResponse<DocumentSubmitResponse> submit(
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "text", required = false) String text) {
        UUID userId = BaseContext.getCurrentId();
        log.info("submit document, userId={}, hasFile={}, textLen={}",
                 userId, file != null && !file.isEmpty(), text == null ? 0 : text.length());
        return ApiResponse.success(documentService.submit(userId, file, text));
    }

    /** Endpoint 2: my submission history. */
    @GetMapping
    public ApiResponse<List<DocumentResponse>> list() {
        return ApiResponse.success(documentService.list(BaseContext.getCurrentId()));
    }

    /** Endpoint 3: detail, includes a 10-minute temporary source URL plus drafts. */
    @GetMapping("/{id}")
    public ApiResponse<DocumentResponse> detail(@PathVariable UUID id) {
        return ApiResponse.success(documentService.detail(BaseContext.getCurrentId(), id));
    }

    /** Endpoint 4: draft list. */
    @GetMapping("/{id}/candidates")
    public ApiResponse<List<CandidateResponse>> candidates(@PathVariable UUID id) {
        return ApiResponse.success(documentService.candidates(BaseContext.getCurrentId(), id));
    }

    /** Endpoint 5: edit a single draft. */
    @PutMapping("/{id}/candidates/{candidateId}")
    public ApiResponse<CandidateResponse> updateCandidate(
            @PathVariable UUID id,
            @PathVariable UUID candidateId,
            @Valid @RequestBody CandidateUpdateRequest request) {
        return ApiResponse.success(
                documentService.updateCandidate(BaseContext.getCurrentId(), id, candidateId, request));
    }

    /** Endpoint 6: bulk confirm, creates the real tasks. */
    @PostMapping("/{id}/confirm")
    public ApiResponse<ConfirmResponse> confirm(
            @PathVariable UUID id,
            @Valid @RequestBody DocumentConfirmRequest request) {
        return ApiResponse.success(
                documentService.confirm(BaseContext.getCurrentId(), id, request));
    }

    /** Endpoint 7: cancel, discards every draft. */
    @PostMapping("/{id}/cancel")
    public ApiResponse<String> cancel(@PathVariable UUID id) {
        documentService.cancel(BaseContext.getCurrentId(), id);
        return ApiResponse.success("Cancelled, nothing was saved.");
    }

    /** Endpoint 8: retry extraction after an AI failure (same material). */
    @PostMapping("/{id}/reextract")
    public ApiResponse<DocumentSubmitResponse> reextract(@PathVariable UUID id) {
        return ApiResponse.success(documentService.reextract(BaseContext.getCurrentId(), id));
    }

    /** Endpoint 9: delete the source (S3 object + drafts + audit rows); tasks are kept. */
    @DeleteMapping("/{id}")
    public ApiResponse<String> delete(@PathVariable UUID id) {
        documentService.delete(BaseContext.getCurrentId(), id);
        return ApiResponse.success("Document deleted.");
    }
}
