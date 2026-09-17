package com.edgar.portfolio.exception;

public class RegistrationConflictException extends RuntimeException {
    public RegistrationConflictException() { super("Unable to register this email"); }
}
