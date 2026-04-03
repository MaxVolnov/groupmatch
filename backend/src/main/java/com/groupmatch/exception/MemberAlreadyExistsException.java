package com.groupmatch.exception;

public class MemberAlreadyExistsException extends RuntimeException {
    public MemberAlreadyExistsException() {
        super("User is already a member of this group");
    }
}
