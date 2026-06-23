package com.groupmatch.repository;

import com.groupmatch.domain.Group;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface GroupRepository extends JpaRepository<Group, UUID> {

    long countByOwnerId(UUID ownerId);

    @EntityGraph(attributePaths = {"owner"})
    Page<Group> findAll(Pageable pageable);

    @EntityGraph(attributePaths = {"owner"})
    Page<Group> findByTitleContainingIgnoreCase(String title, Pageable pageable);
}
