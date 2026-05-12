package com.groupmatch.exception;

public class InviteInvalidException extends RuntimeException {
    public InviteInvalidException() {
        super("Invite is expired, revoked, or has reached its use limit");
    }
}
