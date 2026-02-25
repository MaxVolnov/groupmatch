package com.groupmatch.domain;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/** Составной первичный ключ для GrpMember (grp_id + user_id). */
public class GrpMemberId implements Serializable {

    private UUID group;
    private UUID user;

    public GrpMemberId() {}

    public GrpMemberId(UUID group, UUID user) {
        this.group = group;
        this.user = user;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof GrpMemberId that)) return false;
        return Objects.equals(group, that.group) && Objects.equals(user, that.user);
    }

    @Override
    public int hashCode() {
        return Objects.hash(group, user);
    }
}
