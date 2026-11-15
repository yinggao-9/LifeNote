package come.ying.lifenote.exception;

import come.ying.lifenote.common.ApiResponse.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    //handle custom business exception
    @ExceptionHandler(BusinessException.class)
    public ApiResponse handleBussinessException(BusinessException ex){
        log.warn("Business Exception: {}", ex.getMessage());
        return ApiResponse.fail(ex.getCode(), ex.getMessage());
    }
    //handle MethodArgumentNotValidException
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ApiResponse handleMethodArgumentNotValid(MethodArgumentNotValidException ex){
        Map<String,String> fieldErrors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(err ->
                fieldErrors.put(err.getField(), err.getDefaultMessage())
        );
        log.warn("Method arguments not valid: {}",ex);
        return ApiResponse.fail(400,"validation failed",fieldErrors);
    }
    //handle ConstraintViolationException and MissingServletRequestParameterException
    @ExceptionHandler({ConstraintViolationException.class,
            MissingServletRequestParameterException.class})
    public ApiResponse handleParamError(Exception ex) {
        return ApiResponse.fail(400, ex.getMessage());
    }
    // malformed JSON body: surface as a client error instead of the generic 500 handler
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ApiResponse handleUnreadableBody(HttpMessageNotReadableException ex) {
        return ApiResponse.fail(400, "Request body is missing or malformed");
    }
    // multipart limit exceeded (spring.servlet.multipart.max-file-size); code 1105 matches the upload contract
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ApiResponse handleUploadTooLarge(MaxUploadSizeExceededException ex) {
        return ApiResponse.fail(1105, "File exceeds the 10MB limit");
    }
    //Handle all other unexpected exceptions
    @ExceptionHandler(Exception.class)
    public ApiResponse handleException(Exception ex){
        log.error("System Exception: {}",ex);
        return ApiResponse.fail("System error, please contact administrator");
    }

}
