package come.ying.lifenote.controller;

import come.ying.lifenote.common.ApiResponse.ApiResponse;
import come.ying.lifenote.common.utils.BaseContext;
import come.ying.lifenote.dto.request.PostponeRequest;
import come.ying.lifenote.dto.request.QuickAddRequest;
import come.ying.lifenote.dto.request.TaskCreateRequest;
import come.ying.lifenote.dto.request.TaskStatusRequest;
import come.ying.lifenote.dto.request.TaskUpdateRequest;
import come.ying.lifenote.dto.response.QuickAddResponse;
import come.ying.lifenote.dto.response.TaskResponse;
import come.ying.lifenote.entity.Task;
import come.ying.lifenote.service.IcsExportService;
import come.ying.lifenote.service.TaskService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Task routes: quick add, create, list, detail, patch, status, postpone, .ics export. */
@Slf4j
@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private final TaskService taskService;
    private final IcsExportService icsExportService;   // used only by the .ics export

    public TaskController(TaskService taskService, IcsExportService icsExportService) {
        this.taskService = taskService;
        this.icsExportService = icsExportService;
    }

    /** One-line quick add, parsed locally without calling the AI. */
    @PostMapping("/quick-add")
    public ApiResponse<QuickAddResponse> quickAdd(@Valid @RequestBody QuickAddRequest request) {
        UUID userId = BaseContext.getCurrentId();
        log.info("quick-add, userId={}, textLen={}", userId,
                 request.getText() == null ? 0 : request.getText().length());
        return ApiResponse.success(taskService.quickAdd(userId, request));
    }

    /** Manual create from the form. */
    @PostMapping
    public ApiResponse<TaskResponse> create(@Valid @RequestBody TaskCreateRequest request) {
        return ApiResponse.success(taskService.create(BaseContext.getCurrentId(), request));
    }

    /** List / calendar range; from, to and status are all optional. */
    @GetMapping
    public ApiResponse<List<TaskResponse>> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String status) {
        return ApiResponse.success(taskService.list(BaseContext.getCurrentId(), from, to, status));
    }

    /** Detail view, includes 10-minute temporary source links. */
    @GetMapping("/{id}")
    public ApiResponse<TaskResponse> detail(@PathVariable UUID id) {
        return ApiResponse.success(taskService.detail(BaseContext.getCurrentId(), id));
    }

    /** Partial edit (PATCH, null = unchanged); no @Valid since nothing is required. */
    @PatchMapping("/{id}")
    public ApiResponse<TaskResponse> update(
            @PathVariable UUID id,
            @RequestBody TaskUpdateRequest request) {
        return ApiResponse.success(taskService.update(BaseContext.getCurrentId(), id, request));
    }

    /** Mark done / cancelled / back to todo. */
    @PostMapping("/{id}/status")
    public ApiResponse<TaskResponse> changeStatus(
            @PathVariable UUID id,
            @Valid @RequestBody TaskStatusRequest request) {
        return ApiResponse.success(
                taskService.changeStatus(BaseContext.getCurrentId(), id, request.getStatus()));
    }

    /** Postpone: keep the old task as history, copy to a new date, log the move. */
    @PostMapping("/{id}/postpone")
    public ApiResponse<TaskResponse> postpone(
            @PathVariable UUID id,
            @Valid @RequestBody PostponeRequest request) {
        return ApiResponse.success(taskService.postpone(BaseContext.getCurrentId(), id, request));
    }

    /**
     * Export the task list as an .ics file.
     * Route note: /api/tasks/export.ics never collides with /api/tasks/{id} -
     * literal path segments beat URI variables in Spring's matching, and {id}
     * would fail UUID conversion anyway. Returns a file stream, not an
     * ApiResponse: a JSON envelope would stop browsers from importing it.
     */
    @GetMapping("/export.ics")
    public ResponseEntity<byte[]> exportIcs(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) throws IOException {
        UUID userId = BaseContext.getCurrentId();
        List<Task> tasks = taskService.rawList(userId, from, to, null);   // entities, not DTOs
        byte[] ics = icsExportService.build(tasks);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "text/calendar; charset=utf-8")
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"lifenote.ics\"")
                .body(ics);
    }
}
