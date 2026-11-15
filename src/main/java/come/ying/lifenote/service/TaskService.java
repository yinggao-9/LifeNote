package come.ying.lifenote.service;

import come.ying.lifenote.common.enums.TaskSource;
import come.ying.lifenote.common.enums.TaskStatus;
import come.ying.lifenote.common.utils.QuickAddParser;
import come.ying.lifenote.dto.request.PostponeRequest;
import come.ying.lifenote.dto.request.QuickAddRequest;
import come.ying.lifenote.dto.request.TaskCreateRequest;
import come.ying.lifenote.dto.request.TaskUpdateRequest;
import come.ying.lifenote.dto.response.QuickAddResponse;
import come.ying.lifenote.dto.response.SourceResponse;
import come.ying.lifenote.dto.response.TaskResponse;
import come.ying.lifenote.entity.Postponement;
import come.ying.lifenote.entity.Task;
import come.ying.lifenote.entity.TaskDocument;
import come.ying.lifenote.exception.BusinessException;
import come.ying.lifenote.repository.DocumentRepository;
import come.ying.lifenote.repository.PostponementRepository;
import come.ying.lifenote.repository.TaskDocumentRepository;
import come.ying.lifenote.repository.TaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Endpoints 10-16: three ways to create a task (quick/form/AI) plus list, edit, status, postpone. */
@Service
public class TaskService {

    private final TaskRepository taskRepository;
    private final TaskDocumentRepository taskDocumentRepository;
    private final DocumentRepository documentRepository;
    private final PostponementRepository postponementRepository;
    private final S3StorageService s3StorageService;

    public TaskService(TaskRepository taskRepository,
                       TaskDocumentRepository taskDocumentRepository,
                       DocumentRepository documentRepository,
                       PostponementRepository postponementRepository,
                       S3StorageService s3StorageService) {
        this.taskRepository = taskRepository;
        this.taskDocumentRepository = taskDocumentRepository;
        this.documentRepository = documentRepository;
        this.postponementRepository = postponementRepository;
        this.s3StorageService = s3StorageService;
    }

    // ================================================================
    // Endpoint 10: one-line quick add (local parsing, no AI call)
    // ================================================================
    @Transactional
    public QuickAddResponse quickAdd(UUID userId, QuickAddRequest request) {
        String text = request.getText() == null ? "" : request.getText().trim();
        if (text.isEmpty() || text.length() > 500) {
            throw new BusinessException(1109, "Text is empty or too long.");
        }

        QuickAddParser.Parsed p = QuickAddParser.parse(text, LocalDate.now());

        // no date (or no title) yet: do not create anything, hand the partial
        // parse back as NEED_INPUT so the front end can ask - a normal branch, not an error
        if (!p.dateFound() || p.title() == null || p.title().isBlank()) {
            return QuickAddResponse.builder()
                    .status("NEED_INPUT")
                    .title(p.title())
                    .dueDate(p.dueDate())
                    .dueTime(p.dueTime())
                    .build();
        }

        Task task = newTask(userId, p.title(), p.dueDate(), p.dueTime(), TaskSource.QUICK);
        taskRepository.save(task);
        return QuickAddResponse.builder().status("OK").task(TaskResponse.from(task)).build();
    }

    // ================================================================
    // Endpoint 11: manual form create
    // ================================================================
    @Transactional
    public TaskResponse create(UUID userId, TaskCreateRequest request) {
        Task task = newTask(userId, request.getTitle().trim(),
                request.getDueDate(), request.getDueTime(), TaskSource.MANUAL);
        task.setPlace(request.getPlace());
        task.setDocumentsToBring(request.getDocumentsToBring());
        task.setNotes(request.getNotes());
        taskRepository.save(task);
        return TaskResponse.from(task);
    }

    // ================================================================
    // Endpoint 12: list / calendar range
    // ================================================================
    public List<TaskResponse> list(UUID userId, LocalDate from, LocalDate to, String status) {
        return rawList(userId, from, to, status).stream()
                .map(TaskResponse::from)
                .toList();
    }

    /**
     * Shared range query: the list endpoint wants DTOs, the ICS export (endpoint 19)
     * wants entities. Defaults: from = today, to = from (so no params = today);
     * a reversed range is silently swapped (dragging a calendar selection backwards
     * is common); unknown status string = 1203.
     */
    public List<Task> rawList(UUID userId, LocalDate from, LocalDate to, String status) {
        LocalDate f = from != null ? from : LocalDate.now();
        LocalDate t = to != null ? to : f;
        if (t.isBefore(f)) {
            LocalDate tmp = f; f = t; t = tmp;
        }
        if (status == null || status.isBlank()) {
            return taskRepository.findByUserIdAndDueDateBetweenOrderByDueDateAscDueTimeAsc(userId, f, t);
        }
        TaskStatus st;
        try {
            st = TaskStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(1203, "Unknown status: " + status);
        }
        return taskRepository.findByUserIdAndDueDateBetweenAndStatusOrderByDueDateAscDueTimeAsc(userId, f, t, st);
    }

    // ================================================================
    // Endpoint 13: detail - the only place source URLs are signed
    // ================================================================
    public TaskResponse detail(UUID userId, UUID id) {
        Task task = owned(userId, id);
        TaskResponse response = TaskResponse.from(task);
        response.setSources(sourcesOf(task));
        return response;
    }

    // ================================================================
    // Endpoint 14: edit (PATCH, null = leave unchanged)
    // ================================================================
    @Transactional
    public TaskResponse update(UUID userId, UUID id, TaskUpdateRequest request) {
        Task task = owned(userId, id);

        if (request.getTitle() != null) {
            if (request.getTitle().isBlank()) {
                throw new BusinessException(1109, "Title must not be blank.");
            }
            task.setTitle(request.getTitle().trim());
        }
        // due_date is NOT NULL in the DB: null here means "don't touch it",
        // so no code path can ever write NULL into that column
        if (request.getDueDate() != null) task.setDueDate(request.getDueDate());
        if (request.getDueTime() != null) task.setDueTime(request.getDueTime());
        if (request.getPlace() != null) task.setPlace(request.getPlace());
        if (request.getDocumentsToBring() != null) task.setDocumentsToBring(request.getDocumentsToBring());
        if (request.getNotes() != null) task.setNotes(request.getNotes());

        taskRepository.save(task);
        return TaskResponse.from(task);
    }

    // ================================================================
    // Endpoint 15: mark done / cancelled / back to todo
    // ================================================================
    @Transactional
    public TaskResponse changeStatus(UUID userId, UUID id, String status) {
        Task task = owned(userId, id);

        TaskStatus st;
        try {
            st = TaskStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(1203, "Unknown status: " + status);
        }
        if (st == TaskStatus.POSTPONED) {
            // POSTPONED only comes from endpoint 16 - setting it by hand would
            // skip the copy and the log, leaving a state nothing recorded
            throw new BusinessException(1203, "POSTPONED is set by the postpone action only.");
        }

        task.setStatus(st);
        task.setCompletedAt(st == TaskStatus.DONE ? Instant.now() : null);  // clear on cancel/restore
        taskRepository.save(task);
        return TaskResponse.from(task);
    }

    // ================================================================
    // Endpoint 16: postpone - four writes in one transaction:
    //   1. old task becomes POSTPONED, staying on its old date as history
    //   2. copy created on the new date
    //   3. source links copied to the new task
    //   4. postponements log row
    // ================================================================
    @Transactional
    public TaskResponse postpone(UUID userId, UUID id, PostponeRequest request) {
        Task old = owned(userId, id);

        if (old.getStatus() == TaskStatus.CANCELLED) {
            throw new BusinessException(1102, "A cancelled task cannot be postponed.");
        }
        LocalDate newDate = request.getDueDate();
        if (newDate.isBefore(LocalDate.now()) || newDate.equals(old.getDueDate())) {
            throw new BusinessException(1202,
                    "New date must be in the future and different from the current one.");
        }

        // 1) keep the original as history
        old.setStatus(TaskStatus.POSTPONED);
        taskRepository.save(old);

        // 2) copy to the new date (time carries over when not given)
        Task copy = newTask(userId, old.getTitle(), newDate,
                request.getDueTime() != null ? request.getDueTime() : old.getDueTime(),
                old.getSource());
        copy.setPlace(old.getPlace());
        copy.setDocumentsToBring(old.getDocumentsToBring());
        copy.setNotes(old.getNotes());
        copy.setPostponedFromTaskId(old.getId());     // link back so the detail page can show the chain
        taskRepository.save(copy);

        // 3) source links follow the copy (one task may have notice + poster + email screenshot)
        for (TaskDocument link : taskDocumentRepository.findByTaskId(old.getId())) {
            taskDocumentRepository.save(new TaskDocument(copy.getId(), link.getDocumentId()));
        }

        // 4) log the move
        Postponement log = new Postponement();
        log.setUserId(userId);
        log.setTaskId(old.getId());
        log.setNewTaskId(copy.getId());
        log.setOldDueDate(old.getDueDate());
        log.setOldDueTime(old.getDueTime());
        log.setNewDueDate(copy.getDueDate());
        log.setNewDueTime(copy.getDueTime());
        postponementRepository.save(log);

        TaskResponse response = TaskResponse.from(copy);
        response.setSources(sourcesOf(copy));
        return response;
    }

    // ================= private helpers =================

    /** Constructor shared by all three creation paths: always starts TODO, caller names the source. */
    private Task newTask(UUID userId, String title, LocalDate dueDate, LocalTime dueTime, TaskSource source) {
        Task task = new Task();
        task.setUserId(userId);
        task.setTitle(title);
        task.setDueDate(dueDate);
        task.setDueTime(dueTime);
        task.setSource(source);
        task.setStatus(TaskStatus.TODO);
        // @CreationTimestamp only fills in at flush time, which happens at commit -
        // far after this method returns, so the create response would carry nulls
        task.setCreatedAt(Instant.now());
        task.setUpdatedAt(Instant.now());
        return task;
    }

    /** Ownership check: a miss means "not yours or not there", both answer 1201. */
    private Task owned(UUID userId, UUID taskId) {
        return taskRepository.findByIdAndUserId(taskId, userId)
                .orElseThrow(() -> new BusinessException(1201, "Task not found."));
    }

    /**
     * Build the source list for a detail response, signing a 10-minute URL per
     * linked document. A source may already be gone (endpoint 9 deletes the
     * original but keeps the task); findByIdAndUserId misses are skipped -
     * one fewer source on the page, not an error.
     */
    private List<SourceResponse> sourcesOf(Task task) {
        List<SourceResponse> sources = new ArrayList<>();
        for (TaskDocument link : taskDocumentRepository.findByTaskId(task.getId())) {
            documentRepository.findByIdAndUserId(link.getDocumentId(), task.getUserId())
                    .ifPresent(doc -> sources.add(SourceResponse.builder()
                            .documentId(doc.getId())
                            .filename(doc.getOriginalFilename())
                            .contentType(doc.getContentType())
                            .url(doc.getS3Key() != null ? s3StorageService.presignGetUrl(doc.getS3Key()) : null)
                            .build()));
        }
        return sources;
    }
}
