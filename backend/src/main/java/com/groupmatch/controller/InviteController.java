package com.groupmatch.controller;

import com.groupmatch.dto.invite.CreateInviteRequest;
import com.groupmatch.dto.invite.InvitePreviewResponse;
import com.groupmatch.dto.invite.InviteResponse;
import com.groupmatch.security.UserPrincipal;
import com.groupmatch.service.InviteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class InviteController {

    private final InviteService inviteService;

    @PostMapping("/api/v1/groups/{groupId}/invites")
    @ResponseStatus(HttpStatus.CREATED)
    public InviteResponse create(@AuthenticationPrincipal UserPrincipal principal,
                                 @PathVariable UUID groupId,
                                 @Valid @RequestBody CreateInviteRequest req) {
        return inviteService.createInvite(groupId, principal.getId(), principal.getPlan(), req);
    }

    @GetMapping("/api/v1/groups/{groupId}/invites")
    public List<InviteResponse> list(@AuthenticationPrincipal UserPrincipal principal,
                                     @PathVariable UUID groupId) {
        return inviteService.listInvites(groupId, principal.getId());
    }

    @DeleteMapping("/api/v1/groups/{groupId}/invites/{inviteId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(@AuthenticationPrincipal UserPrincipal principal,
                       @PathVariable UUID groupId,
                       @PathVariable UUID inviteId) {
        inviteService.revokeInvite(inviteId, groupId, principal.getId());
    }

    /**
     * Публичное превью приглашения: что за группа и кто зовёт.
     *
     * Без авторизации — экран приглашения открывается до входа, и именно
     * поэтому здесь нельзя отдавать ничего, кроме двух подписей.
     *
     * Всегда 200, даже на несуществующий токен: ответ идёт в том числе в
     * превью ссылки у мессенджеров, где 404 ломает карточку целиком.
     * Различать случаи — по полю reason.
     */
    @GetMapping("/api/v1/invites/{token}")
    public InvitePreviewResponse preview(@PathVariable String token) {
        return inviteService.previewByToken(token);
    }

    /**
     * Занято ли имя в группе, куда ведёт приглашение. Один бит по строке,
     * которую человек только что набрал, — чтобы предупредить о втором
     * гостевом аккаунте вместо молчаливого дубля в списке участников.
     */
    @GetMapping("/api/v1/invites/{token}/name-taken")
    public Map<String, Boolean> nameTaken(@PathVariable String token,
                                          @RequestParam(name = "name", required = false) String name) {
        return Map.of("taken", inviteService.isNameTakenInInvitedGroup(token, name));
    }

    /** Public token join — authenticated user joins a group via invite link. */
    @PostMapping("/api/v1/invites/{token}/join")
    public InviteResponse join(@AuthenticationPrincipal UserPrincipal principal,
                               @PathVariable String token) {
        return inviteService.joinByToken(token, principal.getId(), principal.getPlan());
    }
}
