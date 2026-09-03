package com.company.itam.common.exception;

import org.springframework.http.HttpStatus;

public class DuplicateResourceException extends AppException {
    public DuplicateResourceException(String message) {
        super(HttpStatus.CONFLICT, "DUPLICATE_CODE", message);
    }

    public DuplicateResourceException(String code, String message) {
        super(HttpStatus.CONFLICT, code, message);
    }
}
