package come.ying.lifenote.controller;

import come.ying.lifenote.common.ApiResponse.ApiResponse;
import come.ying.lifenote.common.utils.BaseContext;
import come.ying.lifenote.dto.response.BoardResponse;
import come.ying.lifenote.dto.response.PostponementResponse;
import come.ying.lifenote.service.BoardService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** The day board and the postpone log, both read-only. */
@Slf4j
@RestController
public class BoardController {

    private final BoardService boardService;

    public BoardController(BoardService boardService) {
        this.boardService = boardService;
    }

    /** One day's board (date omitted = today). */
    @GetMapping("/api/boards")
    public ApiResponse<BoardResponse> board(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        UUID userId = BaseContext.getCurrentId();
        log.info("board, userId={}, date={}", userId, date);
        return ApiResponse.success(boardService.board(userId, date));
    }

    /** Postpone log (from/to omitted = this month). */
    @GetMapping("/api/postponements")
    public ApiResponse<List<PostponementResponse>> postponements(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.success(boardService.postponements(BaseContext.getCurrentId(), from, to));
    }
}
