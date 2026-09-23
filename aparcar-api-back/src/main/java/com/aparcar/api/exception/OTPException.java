package com.aparcar.api.exception;

import lombok.Getter;

@Getter
public class OTPException extends RuntimeException {
    private final OTPExceptionReason reason;

    public OTPException(OTPExceptionReason reason) {
        super(reason.getMessage());
        this.reason = reason;
    }
}
