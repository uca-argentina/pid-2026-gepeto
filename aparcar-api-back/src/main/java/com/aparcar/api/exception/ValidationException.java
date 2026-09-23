package com.aparcar.api.exception;

import lombok.Getter;

import java.util.LinkedList;
import java.util.List;

@Getter
public class ValidationException extends RuntimeException {
    private final List<String> errors;

    public ValidationException(String message) {
        super(message);
        this.errors = new LinkedList<>();
    }

    public ValidationException(String message, List<String> errors) {
        super(message);
        this.errors = errors;
    }
}
