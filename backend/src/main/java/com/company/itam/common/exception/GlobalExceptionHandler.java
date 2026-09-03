package com.company.itam.common.exception;

import com.company.itam.common.response.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.ArrayList;
import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(AppException.class)
    public ResponseEntity<ErrorResponse> handleAppException(AppException ex) {
        log.warn("Business exception: [{}] {}", ex.getCode(), ex.getMessage());
        ErrorResponse error = new ErrorResponse(ex.getMessage(), ex.getCode());
        return ResponseEntity.status(ex.getStatus()).body(error);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException ex) {
        List<String> errors = new ArrayList<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            errors.add(fieldError.getField() + ": " + fieldError.getDefaultMessage());
        }
        log.warn("Validation failed: {}", errors);
        ErrorResponse error = new ErrorResponse("Dữ liệu không hợp lệ", "VALIDATION_ERROR", errors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolationException(DataIntegrityViolationException ex) {
        log.error("Data integrity violation: ", ex);
        String rootMsg = ex.getMostSpecificCause() != null ? ex.getMostSpecificCause().getMessage() : ex.getMessage();
        if (rootMsg != null && (rootMsg.contains("foreign key") || rootMsg.contains("violates foreign key constraint") || rootMsg.contains("is still referenced"))) {
            ErrorResponse error = new ErrorResponse("Danh mục đang được sử dụng trong hệ thống, không thể xóa", "CATALOG_IN_USE");
            return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
        }
        if (rootMsg != null && (rootMsg.contains("unique") || rootMsg.contains("duplicate key"))) {
            ErrorResponse error = new ErrorResponse("Dữ liệu đã tồn tại trong hệ thống", "DUPLICATE_CODE");
            return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
        }
        ErrorResponse error = new ErrorResponse("Ràng buộc toàn vẹn dữ liệu bị vi phạm", "DATA_INTEGRITY_ERROR");
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(IllegalArgumentException ex) {
        log.warn("Illegal argument: {}", ex.getMessage());
        ErrorResponse error = new ErrorResponse(ex.getMessage(), "VALIDATION_ERROR");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneralException(Exception ex) {
        log.error("Unexpected error occurred: ", ex);
        ErrorResponse error = new ErrorResponse("Đã xảy ra lỗi hệ thống. Vui lòng thử lại sau.", "INTERNAL_SERVER_ERROR");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
}
