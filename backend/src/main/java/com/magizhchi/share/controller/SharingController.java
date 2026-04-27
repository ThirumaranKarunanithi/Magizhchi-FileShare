package com.magizhchi.share.controller;

import com.magizhchi.share.dto.request.ShareRequest;
import com.magizhchi.share.dto.response.SharedResourceResponse;
import com.magizhchi.share.model.User;
import com.magizhchi.share.service.SharingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@RestController
@RequestMapping("/api/share")
@RequiredArgsConstructor
public class SharingController {

    private final SharingService sharingService;

    /**
     * POST /api/share
     * Share one or more files with a user or group.
     * Body: { resourceIds, shareType, targetId, permission }
     */
    @PostMapping
    public ResponseEntity<List<SharedResourceResponse>> share(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody ShareRequest req) {
        return ResponseEntity.ok(sharingService.shareResources(user.getId(), req));
    }

    /**
     * GET /api/share/shared-with-me
     * Files directly shared with the current user, or shared with a group they belong to.
     */
    @GetMapping("/shared-with-me")
    public ResponseEntity<List<SharedResourceResponse>> sharedWithMe(
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(sharingService.getSharedWithMe(user.getId()));
    }

    /**
     * GET /api/share/shared-by-me
     * Files the current user has shared with others.
     */
    @GetMapping("/shared-by-me")
    public ResponseEntity<List<SharedResourceResponse>> sharedByMe(
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(sharingService.getSharedByMe(user.getId()));
    }

    /**
     * GET /api/share/in-conversation/{conversationId}
     * Returns all shares visible inside a conversation, determined server-side by the
     * conversation type. Works for both the sharer and the recipient — no extra params needed.
     */
    @GetMapping("/in-conversation/{conversationId}")
    public ResponseEntity<List<SharedResourceResponse>> inConversation(
            @AuthenticationPrincipal User user,
            @PathVariable Long conversationId) {
        return ResponseEntity.ok(
                sharingService.getSharesInConversation(user.getId(), conversationId));
    }

    /**
     * GET /api/share/context?shareType=USER&targetId={userId}
     * GET /api/share/context?shareType=GROUP&targetId={groupId}
     * Returns all shares visible inside a specific conversation:
     *   USER  → bidirectional shares between the caller and another user
     *   GROUP → all shares made to that group (caller must be a member)
     */
    @GetMapping("/context")
    public ResponseEntity<List<SharedResourceResponse>> contextShares(
            @AuthenticationPrincipal User user,
            @RequestParam String shareType,
            @RequestParam Long targetId) {
        return ResponseEntity.ok(
                sharingService.getContextShares(user.getId(), shareType, targetId));
    }

    /**
     * DELETE /api/share/{shareId}
     * Revoke a share. Only the original sharer can revoke.
     */
    @DeleteMapping("/{shareId}")
    public ResponseEntity<Void> revoke(
            @AuthenticationPrincipal User user,
            @PathVariable Long shareId) {
        sharingService.revokeShare(shareId, user.getId());
        return ResponseEntity.noContent().build();
    }
}
