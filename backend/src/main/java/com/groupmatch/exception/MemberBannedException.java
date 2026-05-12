package com.groupmatch.exception;

public class MemberBannedException extends RuntimeException {
    public MemberBannedException() {
        super("This user has been banned from the group");
    }
}
