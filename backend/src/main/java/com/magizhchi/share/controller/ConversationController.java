package com.magizhchi.share.controller;

import com.magizhchi.share.dto.request.CreateGroupRequest;
import com.magizhchi.share.dto.response.ConversationResponse;
import com.magizhchi.share.dto.response.FileMessageResponse;
import com.magizhchi.share.model.User;
import com.magizhchi.share.service.ConversationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/conversations")
@RequiredArgsConstructor
public class ConversationController {

    private final ConversationService convService;

    // ── List all ──────────────────────────────────────────────────────────────

    @GetMapping
    public ResponseEntity<List<ConversationResponse>> list(
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(convService.listForUser(user.getId()));
    }

    // ── Single ────────────────────────────────────────────────────────────────

    @GetMapping("/{id}")
    public ResponseEntity<ConversationResponse> get(
            @PathVariable Long id,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(convService.getDetails(id, user.getId()));
    }

    // ── Personal storage ──────────────────────────────────────────────────────

    @GetMapping("/personal")
    public ResponseEntity<ConversationResponse> getPersonal(
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(convService.getOrCreatePersonal(user.getId()));
    }

    // ── Direct chat ───────────────────────────────────────────────────────────

    @PostMapping("/direct/{targetUserId}")
    public ResponseEntity<ConversationResponse> getOrCreateDirect(
            @PathVariable Long targetUserId,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(
                convService.getOrCreateDirect(user.getId(), targetUserId));
    }

    // ── Group ─────────────────────────────────────────────────────────────────

    @PostMapping("/group")
    public ResponseEntity<ConversationResponse> createGroup(
            @Valid @RequestPart("data") CreateGroupRequest req,
            @RequestPart(value = "icon", required = false) MultipartFile icon,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(convService.createGroup(user.getId(), req, icon));
    }

    @PostMapping("/{id}/members/{userId}")
    public ResponseEntity<ConversationResponse> addMember(
            @PathVariable Long id,
            @PathVariable Long userId,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(convService.addMemberToGroup(id, user.getId(), userId));
    }

    @DeleteMapping("/{id}/members/{userId}")
    public ResponseEntity<Map<String, String>> removeMember(
            @PathVariable Long id,
            @PathVariable Long userId,
            @AuthenticationPrincipal User user) {
        convService.removeMemberFromGroup(id, user.getId(), userId);
        return ResponseEntity.ok(Map.of("message", "Member removed."));
    }

    // ── File history ──────────────────────────────────────────────────────────

    @GetMapping("/{id}/files")
    public ResponseEntity<Page<FileMessageResponse>> fileHistory(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "30") int size,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(convService.getFileHistory(id, user.getId(), page, size));
    }
}
