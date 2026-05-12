package com.groupmatch.repository;

import com.groupmatch.domain.Group;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface GroupRepository extends JpaRepository<Group, UUID> {

    long countByOwnerId(UUID ownerId);
}
