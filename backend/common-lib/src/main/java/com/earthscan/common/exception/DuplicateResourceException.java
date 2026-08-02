package com.earthscan.common.exception;

import org.springframework.http.HttpStatus;

/** Maps to HTTP 409 — e.g. registering an email that already exists. */
public class DuplicateResourceException extends ApiException {

    public DuplicateResourceException(String message) {
        super(HttpStatus.CONFLICT, message);
    }
}
