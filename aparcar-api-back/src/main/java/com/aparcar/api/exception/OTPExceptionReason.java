package com.aparcar.api.exception;

public enum OTPExceptionReason {
    INVALID {
        @Override
        public String getMessage() {
            return "Invalid OTP";
        }
    },

    EXPIRED {
        @Override
        public String getMessage() {
            return "OTP is expired or has been used.";
        }
    };

    public abstract String getMessage();
}
