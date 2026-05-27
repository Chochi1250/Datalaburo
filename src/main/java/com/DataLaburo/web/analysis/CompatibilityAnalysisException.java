package com.DataLaburo.web.analysis;

import org.springframework.http.HttpStatus;

public class CompatibilityAnalysisException extends RuntimeException {
    private final HttpStatus status;

    public CompatibilityAnalysisException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
