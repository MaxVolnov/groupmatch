package com.groupmatch.exception;

public class NotGroupOwnerException extends RuntimeException {
    public NotGroupOwnerException() {
        super("Only the group owner can perform this action");
    }
}
