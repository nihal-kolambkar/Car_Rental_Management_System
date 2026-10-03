package com.veloce.exception;

public class LicenceNotApprovedException extends RuntimeException {
    public LicenceNotApprovedException(String message) {
        super(message);
    }
}
