package com.groupmatch.exception;

public class NotGroupMemberException extends RuntimeException {
    public NotGroupMemberException() {
        super("You are not a member of this group");
    }
}
