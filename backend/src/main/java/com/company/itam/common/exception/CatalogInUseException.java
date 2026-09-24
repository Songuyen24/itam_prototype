package com.company.itam.common.exception;

import org.springframework.http.HttpStatus;

public class CatalogInUseException extends AppException {
    public CatalogInUseException(String message) {
        super(HttpStatus.CONFLICT, "CATALOG_IN_USE", message);
    }
}
