package com.earthscan.common.exception;

import org.springframework.http.HttpStatus;

/** Maps to HTTP 403 — authenticated, but not permitted to touch this particular resource. */
public class ForbiddenException extends ApiException {

    public ForbiddenException(String message) {
        super(HttpStatus.FORBIDDEN, message);
    }
}
