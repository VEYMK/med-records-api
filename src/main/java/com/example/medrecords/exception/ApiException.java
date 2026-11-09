package com.example.medrecords.exception;

import org.springframework.http.HttpStatus;

/**
 * Base class for all "expected" API errors. The global exception handler turns
 * these into RFC 7807 {@code ProblemDetail} responses.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;

    public ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
