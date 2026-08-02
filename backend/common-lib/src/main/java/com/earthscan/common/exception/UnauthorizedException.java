package com.earthscan.common.exception;

import org.springframework.http.HttpStatus;

/** Maps to HTTP 401 — bad credentials or a missing principal. */
public class UnauthorizedException extends ApiException {

    public UnauthorizedException(String message) {
        super(HttpStatus.UNAUTHORIZED, message);
    }
}
