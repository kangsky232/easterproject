package com.smoke.exception;

import com.smoke.common.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<?>> handleBusinessException(BusinessException exception) {
        return ResponseEntity.status(exception.getCode())
                .body(Result.fail(exception.getCode(), exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<?>> handleValidationException(MethodArgumentNotValidException exception) {
        FieldError fieldError = exception.getBindingResult().getFieldError();
        String message = fieldError == null ? "请求参数不正确" : fieldError.getField() + ": " + fieldError.getDefaultMessage();
        return ResponseEntity.badRequest().body(Result.fail(400, message));
    }

    @ExceptionHandler({MethodArgumentTypeMismatchException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<Result<?>> handleMalformedRequest(Exception exception) {
        return ResponseEntity.badRequest().body(Result.fail(400, "请求参数格式不正确"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<?>> handleUnexpectedException(Exception exception) {
        log.error("Unhandled request error", exception);
        return ResponseEntity.internalServerError().body(Result.fail(500, "服务器内部错误"));
    }
}
