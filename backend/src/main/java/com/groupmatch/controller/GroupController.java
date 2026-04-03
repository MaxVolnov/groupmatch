package com.groupmatch.controller;

import com.groupmatch.dto.group.AddMemberRequest;
import com.groupmatch.dto.group.GroupRequest;
import com.groupmatch.dto.group.GroupResponse;
import com.groupmatch.dto.group.MemberResponse;
import com.groupmatch.security.UserPrincipal;
import com.groupmatch.service.GroupService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/groups")
@RequiredArgsConstructor
public class GroupController {

    private final GroupService groupService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GroupResponse create(@AuthenticationPrincipal UserPrincipal principal,
                                @Valid @RequestBody GroupRequest req) {
        return groupService.createGroup(principal.getId(), principal.getPlan(), req);
    }

    @GetMapping
    public List<GroupResponse> list(@AuthenticationPrincipal UserPrincipal principal) {
        return groupService.listGroups(principal.getId());
    }

    @GetMapping("/{id}")
    public GroupResponse get(@AuthenticationPrincipal UserPrincipal principal,
                             @PathVariable UUID id) {
        return groupService.getGroup(id, principal.getId());
    }

    @PutMapping("/{id}")
    public GroupResponse update(@AuthenticationPrincipal UserPrincipal principal,
                                @PathVariable UUID id,
                                @Valid @RequestBody GroupRequest req) {
        return groupService.updateGroup(id, principal.getId(), req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal UserPrincipal principal,
                       @PathVariable UUID id) {
        groupService.deleteGroup(id, principal.getId());
    }

    @GetMapping("/{id}/members")
    public List<MemberResponse> members(@AuthenticationPrincipal UserPrincipal principal,
                                        @PathVariable UUID id) {
        return groupService.getMembers(id, principal.getId());
    }

    @PostMapping("/{id}/members")
    @ResponseStatus(HttpStatus.CREATED)
    public MemberResponse addMember(@AuthenticationPrincipal UserPrincipal principal,
                                    @PathVariable UUID id,
                                    @Valid @RequestBody AddMemberRequest req) {
        return groupService.addMember(id, principal.getId(), principal.getPlan(), req);
    }

    @DeleteMapping("/{id}/members/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeMember(@AuthenticationPrincipal UserPrincipal principal,
                             @PathVariable UUID id,
                             @PathVariable UUID userId) {
        groupService.removeMember(id, principal.getId(), userId);
    }
}
