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
import come.ying.lifenote.service.TaskService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Endpoints 10-16: quick add, manual create, list, detail, patch, status, postpone. */
@Slf4j
@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    /** Endpoint 10: one-line quick add, parsed locally without calling the AI. */
    @PostMapping("/quick-add")
    public ApiResponse<QuickAddResponse> quickAdd(@Valid @RequestBody QuickAddRequest request) {
        UUID userId = BaseContext.getCurrentId();
        log.info("quick-add, userId={}, textLen={}", userId,
                 request.getText() == null ? 0 : request.getText().length());
        return ApiResponse.success(taskService.quickAdd(userId, request));
    }

    /** Endpoint 11: manual create from the form. */
    @PostMapping
    public ApiResponse<TaskResponse> create(@Valid @RequestBody TaskCreateRequest request) {
        return ApiResponse.success(taskService.create(BaseContext.getCurrentId(), request));
    }

    /** Endpoint 12: list / calendar range; from, to and status are all optional. */
    @GetMapping
    public ApiResponse<List<TaskResponse>> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String status) {
        return ApiResponse.success(taskService.list(BaseContext.getCurrentId(), from, to, status));
    }

    /** Endpoint 13: detail, includes 10-minute temporary source links. */
    @GetMapping("/{id}")
    public ApiResponse<TaskResponse> detail(@PathVariable UUID id) {
        return ApiResponse.success(taskService.detail(BaseContext.getCurrentId(), id));
    }

    /** Endpoint 14: partial edit (PATCH, null = unchanged); no @Valid since nothing is required. */
    @PatchMapping("/{id}")
    public ApiResponse<TaskResponse> update(
            @PathVariable UUID id,
            @RequestBody TaskUpdateRequest request) {
        return ApiResponse.success(taskService.update(BaseContext.getCurrentId(), id, request));
    }

    /** Endpoint 15: mark done / cancelled / back to todo. */
    @PostMapping("/{id}/status")
    public ApiResponse<TaskResponse> changeStatus(
            @PathVariable UUID id,
            @Valid @RequestBody TaskStatusRequest request) {
        return ApiResponse.success(
                taskService.changeStatus(BaseContext.getCurrentId(), id, request.getStatus()));
    }

    /** Endpoint 16: postpone - keep the old task as history, copy to a new date, log it. */
    @PostMapping("/{id}/postpone")
    public ApiResponse<TaskResponse> postpone(
            @PathVariable UUID id,
            @Valid @RequestBody PostponeRequest request) {
        return ApiResponse.success(taskService.postpone(BaseContext.getCurrentId(), id, request));
    }
}
