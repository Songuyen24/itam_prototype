package com.company.itam.common.exception;

import org.springframework.http.HttpStatus;

public class AppException extends RuntimeException {
    private final HttpStatus status;
    private final String code;
    private final Object[] args;

    public AppException(HttpStatus status, String code, String message) {
        this(status, code, message, (Object[]) null);
    }

    public AppException(HttpStatus status, String code, String message, Object... args) {
        super(message);
        this.status = status;
        this.code = code;
        this.args = args;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public Object[] getArgs() {
        return args;
    }
}
