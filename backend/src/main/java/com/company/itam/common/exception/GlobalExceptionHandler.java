package com.company.itam.common.exception;

import com.company.itam.common.response.ErrorResponse;
import com.company.itam.common.util.MessageHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.ArrayList;
import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private final MessageHelper messageHelper;

    public GlobalExceptionHandler(MessageHelper messageHelper) {
        this.messageHelper = messageHelper;
    }

    @ExceptionHandler(AppException.class)
    public ResponseEntity<ErrorResponse> handleAppException(AppException ex) {
        log.warn("Business exception: [{}] {}", ex.getCode(), ex.getMessage());
        String localizedMessage = messageHelper.getMessageWithDefault(ex.getCode(), ex.getMessage(), ex.getArgs());
        ErrorResponse error = new ErrorResponse(localizedMessage, ex.getCode());
        return ResponseEntity.status(ex.getStatus()).body(error);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException ex) {
        List<String> errors = new ArrayList<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            errors.add(fieldError.getField() + ": " + fieldError.getDefaultMessage());
        }
        log.warn("Validation failed: {}", errors);
        String localizedMessage = messageHelper.getMessageWithDefault("VALIDATION_ERROR", "Dữ liệu không hợp lệ");
        ErrorResponse error = new ErrorResponse(localizedMessage, "VALIDATION_ERROR", errors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolationException(DataIntegrityViolationException ex) {
        log.error("Data integrity violation: ", ex);
        String rootMsg = ex.getMostSpecificCause() != null ? ex.getMostSpecificCause().getMessage() : ex.getMessage();
        if (rootMsg != null && (rootMsg.contains("foreign key") || rootMsg.contains("violates foreign key constraint") || rootMsg.contains("is still referenced"))) {
            String localizedMsg = messageHelper.getMessageWithDefault("CATALOG_IN_USE", "Danh mục đang được sử dụng trong hệ thống, không thể xóa");
            ErrorResponse error = new ErrorResponse(localizedMsg, "CATALOG_IN_USE");
            return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
        }
        if (rootMsg != null && (rootMsg.contains("unique") || rootMsg.contains("duplicate key"))) {
            String localizedMsg = messageHelper.getMessageWithDefault("DUPLICATE_CODE", "Dữ liệu đã tồn tại trong hệ thống");
            ErrorResponse error = new ErrorResponse(localizedMsg, "DUPLICATE_CODE");
            return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
        }
        String localizedMsg = messageHelper.getMessageWithDefault("DATA_INTEGRITY_ERROR", "Ràng buộc toàn vẹn dữ liệu bị vi phạm");
        ErrorResponse error = new ErrorResponse(localizedMsg, "DATA_INTEGRITY_ERROR");
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDeniedException(AccessDeniedException ex) {
        log.warn("Access denied: {}", ex.getMessage());
        String localizedMsg = messageHelper.getMessageWithDefault("ACCESS_DENIED", "Bạn không có quyền thực hiện thao tác này");
        ErrorResponse error = new ErrorResponse(localizedMsg, "ACCESS_DENIED");
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(IllegalArgumentException ex) {
        log.warn("Illegal argument: {}", ex.getMessage());
        String localizedMsg = messageHelper.getMessageWithDefault("VALIDATION_ERROR", ex.getMessage());
        ErrorResponse error = new ErrorResponse(localizedMsg, "VALIDATION_ERROR");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneralException(Exception ex) {
        log.error("Unexpected error occurred: ", ex);
        String localizedMsg = messageHelper.getMessageWithDefault("INTERNAL_SERVER_ERROR", "Đã xảy ra lỗi hệ thống. Vui lòng thử lại sau.");
        ErrorResponse error = new ErrorResponse(localizedMsg, "INTERNAL_SERVER_ERROR");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
}
