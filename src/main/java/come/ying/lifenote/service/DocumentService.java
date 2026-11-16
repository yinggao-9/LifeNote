package come.ying.lifenote.service;

import come.ying.lifenote.common.enums.DocType;
import come.ying.lifenote.common.enums.DocumentStatus;
import come.ying.lifenote.common.enums.TaskSource;
import come.ying.lifenote.dto.request.CandidateUpdateRequest;
import come.ying.lifenote.dto.request.DocumentConfirmRequest;
import come.ying.lifenote.dto.response.*;
import come.ying.lifenote.entity.Document;
import come.ying.lifenote.entity.Task;
import come.ying.lifenote.entity.TaskCandidate;
import come.ying.lifenote.entity.TaskDocument;
import come.ying.lifenote.exception.BusinessException;
import come.ying.lifenote.repository.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Document intake and draft confirmation in one place. Two deliberate rules:
 * - no @Transactional around submit(): S3 upload plus the LLM call take
 *   seconds, and a held DB connection during that time starves the pool;
 *   each save below runs in its own short transaction instead;
 * - AI failure returns HTTP 200 with status FAILED (the file is already
 *   stored and can be retried), while client errors throw BusinessException.
 */
@Slf4j
@Service
public class DocumentService {

    private static final long MAX_FILE_SIZE = 10L * 1024 * 1024;      // 10MB
    private static final int  MAX_TEXT_LEN  = 20_000;
    private static final List<String> ALLOWED_TYPES = List.of(
            "application/pdf", "image/png", "image/jpeg", "image/jpg", "image/webp");

    private final DocumentRepository documentRepository;
    private final TaskCandidateRepository candidateRepository;
    private final TaskRepository taskRepository;
    private final TaskDocumentRepository taskDocumentRepository;
    private final S3StorageService s3StorageService;
    private final PdfTextExtractor pdfTextExtractor;
    private final AiExtractionService aiExtractionService;
    private final AiRunService aiRunService;

    public DocumentService(DocumentRepository documentRepository,
                           TaskCandidateRepository candidateRepository,
                           TaskRepository taskRepository,
                           TaskDocumentRepository taskDocumentRepository,
                           S3StorageService s3StorageService,
                           PdfTextExtractor pdfTextExtractor,
                           AiExtractionService aiExtractionService,
                           AiRunService aiRunService) {
        this.documentRepository = documentRepository;
        this.candidateRepository = candidateRepository;
        this.taskRepository = taskRepository;
        this.taskDocumentRepository = taskDocumentRepository;
        this.s3StorageService = s3StorageService;
        this.pdfTextExtractor = pdfTextExtractor;
        this.aiExtractionService = aiExtractionService;
        this.aiRunService = aiRunService;
    }

    // ================================================================
    // submit
    // ================================================================
    public DocumentSubmitResponse submit(UUID userId, MultipartFile file, String text) {
        boolean hasFile = file != null && !file.isEmpty();
        boolean hasText = text != null && !text.isBlank();

        // client mistakes throw; server-side trouble below returns a FAILED status
        if (!hasFile && !hasText) {
            throw new BusinessException(1109, "Please provide text or a file.");
        }
        if (hasText && text.length() > MAX_TEXT_LEN) {
            throw new BusinessException(1109, "Text is too long (max 20000 characters).");
        }

        DocType docType;
        String originalName = null, contentType = null;
        byte[] fileBytes = null;
        if (hasFile) {
            contentType = file.getContentType();
            if (contentType == null || !ALLOWED_TYPES.contains(contentType.toLowerCase())) {
                throw new BusinessException(1104,
                        "Unsupported file type. Only PDF, PNG, JPG, WEBP are allowed.");
            }
            if (file.getSize() > MAX_FILE_SIZE) {
                throw new BusinessException(1105, "File is larger than 10MB.");
            }
            try {
                fileBytes = file.getBytes();
            } catch (Exception e) {
                throw new BusinessException(1105, "Cannot read the file: " + e.getMessage());
            }
            originalName = file.getOriginalFilename();
            docType = contentType.toLowerCase().contains("pdf") ? DocType.PDF : DocType.IMAGE;
        } else {
            docType = DocType.TEXT;
        }

        // persist first: the S3 key is built from the document id
        Document doc = new Document();
        doc.setUserId(userId);
        doc.setDocType(docType);
        doc.setOriginalFilename(originalName);
        doc.setContentType(contentType);
        doc.setFileSize(hasFile ? file.getSize() : null);
        doc.setStatus(DocumentStatus.PENDING_CONFIRM);
        doc = documentRepository.save(doc);

        try {
            if (hasFile) {
                String key = s3StorageService.buildKey(userId, doc.getId(), originalName);
                s3StorageService.upload(key, fileBytes, contentType);
                doc.setS3Key(key);
                documentRepository.save(doc);
            }

            String inputText;
            if (docType == DocType.TEXT) {
                inputText = text.trim();
                doc.setRawText(inputText);
            } else if (docType == DocType.PDF) {
                inputText = pdfTextExtractor.extractText(fileBytes);   // throws 1204
                doc.setRawText(inputText);
            } else {
                inputText = null;                                      // images go to the model as-is
            }
            documentRepository.save(doc);

            return runExtraction(doc, userId, inputText, fileBytes, contentType);

        } catch (BusinessException e) {
            // unreadable PDF or S3 outage: keep the row in FAILED so the user can retry or delete
            doc.setStatus(DocumentStatus.FAILED);
            doc.setErrorMessage(e.getMessage());
            documentRepository.save(doc);
            log.warn("document {} failed: {}", doc.getId(), e.getMessage());
            return DocumentSubmitResponse.builder()
                    .documentId(doc.getId())
                    .docType(docType.name())
                    .filename(originalName)
                    .status(DocumentStatus.FAILED.name())
                    .errorMessage(e.getMessage())
                    .candidates(List.of())
                    .build();
        }
    }

    /**
     * The AI call plus persisting its result. No @Transactional on purpose:
     * the slow call must not sit inside a DB transaction (see class comment).
     */
    private DocumentSubmitResponse runExtraction(Document doc, UUID userId,
                                                 String inputText, byte[] imageBytes,
                                                 String contentType) {
        long start = System.currentTimeMillis();
        try {
            String preferred = aiExtractionService.preferredModelFor(userId);
            AiExtractionService.AiExtraction extraction = aiExtractionService.extract(
                    preferred,
                    inputText,
                    doc.getDocType() == DocType.IMAGE ? imageBytes : null,
                    contentType);
            long cost = System.currentTimeMillis() - start;

            // audit row: prompt version, model, raw output, duration
            UUID runId = aiRunService.recordSuccess(doc.getId(), userId,
                    extraction.model(), extraction.raw(), cost);

            // drafts (zero rows is a valid outcome: nothing to do in this material)
            candidateRepository.deleteByDocumentId(doc.getId());   // clear leftovers on retry
            List<CandidateResponse> vos = new ArrayList<>();
            for (ExtractedTask r : extraction.tasks()) {
                TaskCandidate c = new TaskCandidate();
                c.setDocumentId(doc.getId());
                c.setRunId(runId);        // which extraction produced this draft
                c.setUserId(userId);
                c.setTitle(cut(r.getTitle(), 200, "Untitled task"));
                c.setDueDate(parseDate(r.getDueDate()));
                c.setDueTime(parseTime(r.getDueTime()));
                c.setPlace(cut(r.getPlace(), 200, null));
                c.setDocumentsToBring(r.getDocumentsToBring());
                c.setNotes(cut(r.getNotes(), 2000, null));
                c.setConfidence(r.getConfidence());
                c = candidateRepository.save(c);
                vos.add(CandidateResponse.from(c));
            }

            doc.setStatus(DocumentStatus.PENDING_CONFIRM);
            doc.setErrorMessage(null);
            documentRepository.save(doc);

            return DocumentSubmitResponse.builder()
                    .documentId(doc.getId())
                    .docType(doc.getDocType().name())
                    .filename(doc.getOriginalFilename())
                    .status(DocumentStatus.PENDING_CONFIRM.name())
                    .extractedText(cut(doc.getRawText(), 2000, null))
                    .candidates(vos)
                    .build();

        } catch (BusinessException e) {
            // call failed or reply was not valid JSON: audit, status and response all updated
            long cost = System.currentTimeMillis() - start;
            aiRunService.recordFailure(doc.getId(), userId,
                    aiExtractionService.preferredModelFor(userId), e, cost);

            doc.setStatus(DocumentStatus.FAILED);
            doc.setErrorMessage(e.getMessage());
            documentRepository.save(doc);

            return DocumentSubmitResponse.builder()
                    .documentId(doc.getId())
                    .docType(doc.getDocType().name())
                    .filename(doc.getOriginalFilename())
                    .status(DocumentStatus.FAILED.name())
                    .errorMessage(e.getMessage())
                    .candidates(List.of())
                    .build();
        }
    }

    // ================================================================
    // history list
    // ================================================================
    public List<DocumentResponse> list(UUID userId) {
        return documentRepository.findTop50ByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(DocumentResponse::brief)
                .toList();
    }

    // ================================================================
    // detail (fresh presigned source URL + drafts)
    // ================================================================
    public DocumentResponse detail(UUID userId, UUID id) {
        Document doc = owned(userId, id);
        DocumentResponse.DocumentResponseBuilder b = DocumentResponse.brief(doc)
                .toBuilder()
                .rawText(cut(doc.getRawText(), 5000, null));
        if (doc.getS3Key() != null) {
            // presigned URLs expire in 10 minutes: sign now, never store
            b.sourceUrl(s3StorageService.presignGetUrl(doc.getS3Key()));
        }
        b.candidates(candidateRepository
                .findByDocumentIdAndUserIdOrderByCreatedAt(id, userId).stream()
                .map(CandidateResponse::from).toList());
        return b.build();
    }

    // ================================================================
    // draft list
    // ================================================================
    public List<CandidateResponse> candidates(UUID userId, UUID docId) {
        owned(userId, docId);                       // ownership check rides along
        return candidateRepository.findByDocumentIdAndUserIdOrderByCreatedAt(docId, userId).stream()
                .map(CandidateResponse::from).toList();
    }

    // ================================================================
    // edit one draft
    // ================================================================
    @Transactional
    public CandidateResponse updateCandidate(UUID userId, UUID docId, UUID candId,
                                             CandidateUpdateRequest req) {
        Document doc = owned(userId, docId);
        requirePending(doc);
        TaskCandidate c = candidateRepository
                .findByIdAndUserIdAndDocumentId(candId, userId, docId)
                .orElseThrow(() -> new BusinessException(1106, "Candidate not found."));
        if (req.getTitle() == null || req.getTitle().isBlank()) {
            throw new BusinessException(1107, "Title must not be blank.");
        }
        c.setTitle(req.getTitle().trim());
        c.setDueDate(req.getDueDate());
        c.setDueTime(req.getDueTime());
        c.setPlace(req.getPlace());
        c.setDocumentsToBring(req.getDocumentsToBring());
        c.setNotes(req.getNotes());
        c = candidateRepository.save(c);
        return CandidateResponse.from(c);
    }

    // ================================================================
    // bulk confirm (one transaction; any bad row rolls back all)
    // ================================================================
    @Transactional
    public ConfirmResponse confirm(UUID userId, UUID docId, DocumentConfirmRequest req) {
        Document doc = owned(userId, docId);
        requirePending(doc);

        List<CandidateUpdateRequest> items = req.getCandidates();
        if (items == null || items.isEmpty()) {
            throw new BusinessException(1108,
                    "Nothing to confirm. Delete the items or press Cancel instead.");
        }

        // first pass: validate everything before touching the DB, so a failure
        // halfway never leaves half-created tasks behind
        for (CandidateUpdateRequest item : items) {
            if (item.getId() == null) {
                throw new BusinessException(1106, "Candidate id is missing, please reload.");
            }
            if (item.getTitle() == null || item.getTitle().isBlank()) {
                throw new BusinessException(1107, "Every task needs a title.");
            }
            if (item.getDueDate() == null) {
                // "a missing date is asked for" - the dialog marks it, this is the safety net
                throw new BusinessException(1107,
                        "Task '" + item.getTitle() + "' is missing a date.");
            }
        }

        // second pass: write the user's edits back to the draft (keeps the final
        // values next to the model's original ones), then create the task
        List<UUID> taskIds = new ArrayList<>();
        for (CandidateUpdateRequest item : items) {
            TaskCandidate c = candidateRepository
                    .findByIdAndUserIdAndDocumentId(item.getId(), userId, docId)
                    .orElseThrow(() -> new BusinessException(1106, "Candidate not found."));

            c.setTitle(item.getTitle().trim());
            c.setDueDate(item.getDueDate());
            c.setDueTime(item.getDueTime());
            c.setPlace(item.getPlace());
            c.setDocumentsToBring(item.getDocumentsToBring());
            c.setNotes(item.getNotes());
            c.setStatus("CONFIRMED");
            candidateRepository.save(c);

            Task task = new Task();
            task.setUserId(userId);
            task.setTitle(c.getTitle());
            task.setNotes(c.getNotes());
            task.setDueDate(c.getDueDate());
            task.setDueTime(c.getDueTime());
            task.setPlace(c.getPlace());
            task.setDocumentsToBring(c.getDocumentsToBring());
            task.setSource(TaskSource.AI);
            task = taskRepository.save(task);

            // link source material so the task page can show the original document
            taskDocumentRepository.save(new TaskDocument(task.getId(), docId));
            taskIds.add(task.getId());
        }

        doc.setStatus(DocumentStatus.CONFIRMED);
        documentRepository.save(doc);

        return ConfirmResponse.builder()
                .taskIds(taskIds)
                .message(taskIds.size() + " task(s) added to your list.")
                .build();
    }

    // ================================================================
    // cancel
    // ================================================================
    @Transactional
    public void cancel(UUID userId, UUID docId) {
        Document doc = owned(userId, docId);
        requirePending(doc);
        candidateRepository.deleteByDocumentId(docId);   // physical delete: cancelling saves nothing
        doc.setStatus(DocumentStatus.CANCELLED);
        documentRepository.save(doc);
    }

    // ================================================================
    // re-extract (another AI call on the same material)
    // ================================================================
    public DocumentSubmitResponse reextract(UUID userId, UUID docId) {
        Document doc = owned(userId, docId);
        if (doc.getStatus() == DocumentStatus.CONFIRMED) {
            throw new BusinessException(1102, "Already confirmed, cannot re-extract.");
        }
        candidateRepository.deleteByDocumentId(docId);   // drop leftovers from the failed run

        byte[] imageBytes = null;
        String inputText = doc.getRawText();
        if (doc.getDocType() == DocType.IMAGE && doc.getS3Key() != null) {
            imageBytes = s3StorageService.download(doc.getS3Key());
        } else if ((doc.getDocType() == DocType.PDF) && inputText == null && doc.getS3Key() != null) {
            try {
                inputText = pdfTextExtractor.extractText(s3StorageService.download(doc.getS3Key()));
                doc.setRawText(inputText);
            } catch (BusinessException e) {
                doc.setStatus(DocumentStatus.FAILED);
                doc.setErrorMessage(e.getMessage());
                documentRepository.save(doc);
                throw e;
            }
        }
        return runExtraction(doc, userId, inputText, imageBytes, doc.getContentType());
    }

    // ================================================================
    // delete the source
    // ================================================================
    @Transactional
    public void delete(UUID userId, UUID docId) {
        Document doc = owned(userId, docId);
        if (doc.getS3Key() != null) {
            s3StorageService.delete(doc.getS3Key());
        }
        // drafts, audit rows and task_documents go with the FK cascade;
        // confirmed tasks survive - they are the user's work, just no longer linked
        documentRepository.delete(doc);
    }

    // ================= private helpers =================

    /** Ownership check: querying by id+userId, a miss means 1101 (wrong user or gone). */
    private Document owned(UUID userId, UUID docId) {
        return documentRepository.findByIdAndUserId(docId, userId)
                .orElseThrow(() -> new BusinessException(1101, "Document not found."));
    }

    private void requirePending(Document doc) {
        if (doc.getStatus() != DocumentStatus.PENDING_CONFIRM) {
            throw new BusinessException(1102,
                    "Document is " + doc.getStatus() + ", this action is not allowed.");
        }
    }

    private String cut(String s, int max, String fallback) {
        if (s == null || s.isBlank()) return fallback;
        return s.length() <= max ? s : s.substring(0, max);
    }

    private java.time.LocalDate parseDate(String s) {
        if (s == null || s.isBlank()) return null;
        try { return java.time.LocalDate.parse(s.trim()); } catch (Exception e) { return null; }
    }

    private java.time.LocalTime parseTime(String s) {
        if (s == null || s.isBlank()) return null;
        try { return java.time.LocalTime.parse(s.trim()); } catch (Exception e) { return null; }
    }
}
