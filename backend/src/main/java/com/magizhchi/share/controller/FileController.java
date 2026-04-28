package com.magizhchi.share.controller;

import com.magizhchi.share.dto.response.FileMessageResponse;
import com.magizhchi.share.exception.AppException;
import com.magizhchi.share.model.FileMessage;
import com.magizhchi.share.model.User;
import com.magizhchi.share.service.ConversationService;
import com.magizhchi.share.service.FileMessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * POST   /api/files/send/{conversationId}        — upload + send file
 * POST   /api/files/send-folder/{conversationId}  — upload folder
 * GET    /api/files/{id}/download-url             — presigned download URL (enforces permission)
 * GET    /api/files/{id}/preview-url              — presigned inline URL (always allowed)
 * GET    /api/files/{id}/thumbnail-url            — presigned thumbnail URL
 * POST   /api/files/{id}/pin                      — pin file for caller
 * DELETE /api/files/{id}/pin                      — unpin file for caller
 * GET    /api/files/pinned?conversationId=…        — list caller's pinned files in a conversation
 * PATCH  /api/files/{id}/permissions              — change downloadPermission (sender or admin)
 * GET    /api/files/search?q=…                    — search files by name/caption
 * DELETE /api/files/{id}                          — soft-delete (sender or admin)
 */
@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {

    private final FileMessageService  fileService;
    private final ConversationService convService;

    // ── Upload ────────────────────────────────────────────────────────────────

    /** Upload a single file (optionally with a caption and @mention list). */
    @PostMapping("/send/{conversationId}")
    public ResponseEntity<FileMessageResponse> sendFile(
            @PathVariable Long conversationId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "caption",           required = false) String caption,
            @RequestParam(value = "folderPath",         required = false) String folderPath,
            @RequestParam(value = "mentionedUserIds",   required = false) String mentionedUserIds,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(
                fileService.sendFile(conversationId, user.getId(), file, caption, folderPath, mentionedUserIds));
    }

    /**
     * Upload all files from a folder in one request.
     * Accepts parallel arrays: files[] and relativePaths[].
     * relativePaths[i] = the browser's webkitRelativePath for files[i],
     * e.g. "MyFolder/subfolder/photo.jpg"
     */
    @PostMapping("/send-folder/{conversationId}")
    public ResponseEntity<List<FileMessageResponse>> sendFolder(
            @PathVariable Long conversationId,
            @RequestParam("files") MultipartFile[] files,
            @RequestParam(value = "relativePaths", required = false) String[] relativePaths,
            @RequestParam(value = "caption",       required = false) String caption,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(
                fileService.sendFolder(conversationId, user.getId(), files, relativePaths, caption));
    }

    // ── URL generation ────────────────────────────────────────────────────────

    /** Presigned download URL — enforces downloadPermission. */
    @GetMapping("/{id}/download-url")
    public ResponseEntity<Map<String, String>> downloadUrl(
            @PathVariable Long id,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(Map.of("url",
                fileService.getDownloadUrl(id, user.getId())));
    }

    /**
     * Presigned inline URL — for in-browser preview.
     * Always accessible to any conversation member regardless of downloadPermission.
     */
    @GetMapping("/{id}/preview-url")
    public ResponseEntity<Map<String, String>> previewUrl(
            @PathVariable Long id,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(Map.of("url",
                fileService.getPreviewUrl(id, user.getId())));
    }

    /** Presigned thumbnail URL (images/videos only). */
    @GetMapping("/{id}/thumbnail-url")
    public ResponseEntity<Map<String, String>> thumbnailUrl(
            @PathVariable Long id,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(Map.of("url",
                fileService.getThumbnailUrl(id, user.getId())));
    }

    // ── Pin / Unpin ───────────────────────────────────────────────────────────

    /** Pin a file for the authenticated user. */
    @PostMapping("/{id}/pin")
    public ResponseEntity<FileMessageResponse> pin(
            @PathVariable Long id,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(fileService.pinFile(id, user.getId()));
    }

    /** Unpin a file for the authenticated user. */
    @DeleteMapping("/{id}/pin")
    public ResponseEntity<FileMessageResponse> unpin(
            @PathVariable Long id,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(fileService.unpinFile(id, user.getId()));
    }

    /** List all files pinned by the authenticated user in a conversation. */
    @GetMapping("/pinned")
    public ResponseEntity<List<FileMessageResponse>> pinned(
            @RequestParam Long conversationId,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(fileService.getPinnedFiles(conversationId, user.getId()));
    }

    // ── Permissions ───────────────────────────────────────────────────────────

    /**
     * Change the download permission for a file.
     * Body: { "permission": "VIEW_ONLY" | "CAN_DOWNLOAD" | "ADMIN_ONLY_DOWNLOAD" }
     */
    @PatchMapping("/{id}/permissions")
    public ResponseEntity<FileMessageResponse> setPermission(
            @PathVariable Long id,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal User user) {
        String raw = body.get("permission");
        if (raw == null || raw.isBlank()) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Permission value is required.");
        }
        FileMessage.DownloadPermission perm;
        try {
            perm = FileMessage.DownloadPermission.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new AppException(HttpStatus.BAD_REQUEST,
                    "Invalid permission value. Must be VIEW_ONLY, CAN_DOWNLOAD, or ADMIN_ONLY_DOWNLOAD.");
        }
        return ResponseEntity.ok(fileService.setPermission(id, user.getId(), perm));
    }

    // ── Search ────────────────────────────────────────────────────────────────

    /**
     * Search files by filename or caption across all conversations the caller is a member of.
     * GET /api/files/search?q=invoice
     */
    @GetMapping("/search")
    public ResponseEntity<List<FileMessageResponse>> search(
            @RequestParam String q,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(convService.searchFiles(user.getId(), q));
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> delete(
            @PathVariable Long id,
            @AuthenticationPrincipal User user) {
        fileService.deleteMessage(id, user.getId());
        return ResponseEntity.ok(Map.of("message", "File deleted."));
    }
}
