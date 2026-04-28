package com.magizhchi.share.controller;

import com.magizhchi.share.dto.request.CreateFolderRequest;
import com.magizhchi.share.dto.response.FolderResponse;
import com.magizhchi.share.model.User;
import com.magizhchi.share.service.FolderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * POST   /api/folders                                 — create folder
 * GET    /api/folders?conversationId=&parentFolderId= — list folders (root or children)
 * GET    /api/folders/{id}/breadcrumb                 — breadcrumb path from root
 * PATCH  /api/folders/{id}                            — rename folder
 * DELETE /api/folders/{id}                            — soft-delete folder (recursive)
 */
@RestController
@RequestMapping("/api/folders")
@RequiredArgsConstructor
public class FolderController {

    private final FolderService folderService;

    /** Create a new folder (root or nested inside parentFolderId). */
    @PostMapping
    public ResponseEntity<FolderResponse> create(
            @Valid @RequestBody CreateFolderRequest req,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(folderService.createFolder(req, user.getId()));
    }

    /**
     * List folders in a conversation.
     * If parentFolderId is omitted → returns root folders.
     * If parentFolderId is provided → returns direct children of that folder.
     */
    @GetMapping
    public ResponseEntity<List<FolderResponse>> list(
            @RequestParam Long conversationId,
            @RequestParam(required = false) Long parentFolderId,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(
                folderService.listFolders(conversationId, parentFolderId, user.getId()));
    }

    /** Get the breadcrumb path from the root down to the given folder. */
    @GetMapping("/{id}/breadcrumb")
    public ResponseEntity<List<FolderResponse>> breadcrumb(
            @PathVariable Long id,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(folderService.getBreadcrumb(id, user.getId()));
    }

    /**
     * Rename a folder.
     * Body: { "name": "New Name" }
     */
    @PatchMapping("/{id}")
    public ResponseEntity<FolderResponse> rename(
            @PathVariable Long id,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(
                folderService.renameFolder(id, body.get("name"), user.getId()));
    }

    /** Soft-delete a folder and all its children recursively. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> delete(
            @PathVariable Long id,
            @AuthenticationPrincipal User user) {
        folderService.deleteFolder(id, user.getId());
        return ResponseEntity.ok(Map.of("message", "Folder deleted."));
    }
}
