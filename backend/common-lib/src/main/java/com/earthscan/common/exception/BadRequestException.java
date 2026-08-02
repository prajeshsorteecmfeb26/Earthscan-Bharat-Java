package com.earthscan.common.exception;

import org.springframework.http.HttpStatus;

/** Maps to HTTP 400 for semantic problems that bean validation cannot express. */
public class BadRequestException extends ApiException {

    public BadRequestException(String message) {
        super(HttpStatus.BAD_REQUEST, message);
    }
}
