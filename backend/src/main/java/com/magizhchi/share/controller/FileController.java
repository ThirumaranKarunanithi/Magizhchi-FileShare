package com.magizhchi.share.controller;

import com.magizhchi.share.dto.response.FileMessageResponse;
import com.magizhchi.share.model.User;
import com.magizhchi.share.service.ConversationService;
import com.magizhchi.share.service.FileMessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * POST /api/files/send/{conversationId}  — upload + send file
 * GET  /api/files/{id}/download-url      — get presigned download URL
 * GET  /api/files/{id}/thumbnail-url     — get presigned thumbnail URL
 * DELETE /api/files/{id}                 — soft-delete
 */
@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {

    private final FileMessageService  fileService;
    private final ConversationService convService;

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

    @GetMapping("/{id}/download-url")
    public ResponseEntity<Map<String, String>> downloadUrl(
            @PathVariable Long id,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(Map.of("url",
                fileService.getDownloadUrl(id, user.getId())));
    }

    @GetMapping("/{id}/thumbnail-url")
    public ResponseEntity<Map<String, String>> thumbnailUrl(
            @PathVariable Long id,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(Map.of("url",
                fileService.getThumbnailUrl(id, user.getId())));
    }

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

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> delete(
            @PathVariable Long id,
            @AuthenticationPrincipal User user) {
        fileService.deleteMessage(id, user.getId());
        return ResponseEntity.ok(Map.of("message", "File deleted."));
    }
}
