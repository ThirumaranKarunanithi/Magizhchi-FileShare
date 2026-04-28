package com.magizhchi.share.service;

import com.magizhchi.share.dto.response.FileMessageResponse;
import com.magizhchi.share.exception.AppException;
import com.magizhchi.share.model.ConversationMember;
import com.magizhchi.share.model.FileMessage;
import com.magizhchi.share.model.User;
import com.magizhchi.share.model.UserFilePin;
import com.magizhchi.share.repository.ConversationMemberRepository;
import com.magizhchi.share.repository.ConversationRepository;
import com.magizhchi.share.repository.FileMessageRepository;
import com.magizhchi.share.repository.UserFilePinRepository;
import com.magizhchi.share.repository.UserRepository;
import com.magizhchi.share.websocket.FileMessageEvent;
import com.magizhchi.share.websocket.NotificationEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class FileMessageService {

    private final FileMessageRepository        msgRepo;
    private final ConversationRepository       convRepo;
    private final ConversationMemberRepository memberRepo;
    private final UserRepository               userRepo;
    private final FileStorageService           storage;
    private final ConversationService          convService;
    private final ConnectionService            connService;
    private final SimpMessagingTemplate        messagingTemplate;
    private final SharingService               sharingService;
    private final UserFilePinRepository        pinRepo;
    private final ActivityService              activityService;
    private final FolderService                folderService;

    /**
     * Upload a file, create the FileMessage record, and push a WebSocket event
     * to all members of the conversation.
     */
    @Transactional
    public FileMessageResponse sendFile(Long conversationId, Long senderId,
                                        MultipartFile file, String caption) {
        return sendFile(conversationId, senderId, file, caption, null, null, null);
    }

    @Transactional
    public FileMessageResponse sendFile(Long conversationId, Long senderId,
                                        MultipartFile file, String caption, String folderPath) {
        return sendFile(conversationId, senderId, file, caption, folderPath, null, null);
    }

    @Transactional
    public FileMessageResponse sendFile(Long conversationId, Long senderId,
                                        MultipartFile file, String caption,
                                        String folderPath, String mentionedUserIds) {
        return sendFile(conversationId, senderId, file, caption, folderPath, mentionedUserIds, null);
    }

    @Transactional
    public FileMessageResponse sendFile(Long conversationId, Long senderId,
                                        MultipartFile file, String caption,
                                        String folderPath, String mentionedUserIds,
                                        FileMessage.DownloadPermission downloadPermission) {
        // Check membership
        if (!memberRepo.existsByConversationIdAndUserIdAndIsActiveTrue(conversationId, senderId)) {
            throw new AppException(HttpStatus.FORBIDDEN, "You are not a member of this conversation.");
        }

        var conv   = convRepo.findById(conversationId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Conversation not found."));
        User sender = userRepo.findById(senderId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Sender not found."));

        // Privacy guard: for DIRECT chats, require an active connection
        if (conv.getType() == com.magizhchi.share.model.Conversation.ConversationType.DIRECT) {
            conv.getMembers().stream()
                    .filter(m -> m.getIsActive() && !m.getUser().getId().equals(senderId))
                    .findFirst()
                    .ifPresent(m -> connService.requireConnected(senderId, m.getUser().getId()));
        }

        // ── Storage limit check (use live sum for accuracy) ──────────────────
        long fileSize    = file.getSize();
        long currentUsed = msgRepo.sumFileSizeByUser(senderId);
        if (currentUsed + fileSize > sender.getMaxStorageBytes()) {
            throw new AppException(HttpStatus.PAYLOAD_TOO_LARGE,
                    "Storage limit exceeded. Upgrade your plan to upload more files.");
        }

        // Upload to S3
        String s3Key = storage.uploadFile(file, conversationId);

        FileMessage.DownloadPermission effectivePerm =
                downloadPermission != null ? downloadPermission : FileMessage.DownloadPermission.CAN_DOWNLOAD;

        // If the file lands inside a folder that is (or has any ancestor) marked
        // VIEW_ONLY, lock the file's permission to VIEW_ONLY regardless of the
        // requested value. The whole subtree of a view-only folder stays view-only.
        if (folderPath != null && !folderPath.isBlank()) {
            boolean inViewOnlyChain = folderService
                    .findFolderByPath(conversationId, folderPath)
                    .map(folderService::isViewOnlyChain)
                    .orElse(false);
            if (inViewOnlyChain) {
                effectivePerm = FileMessage.DownloadPermission.VIEW_ONLY;
            }
        }

        FileMessage msg = FileMessage.builder()
                .conversation(conv)
                .sender(sender)
                .originalFileName(file.getOriginalFilename())
                .contentType(file.getContentType())
                .fileSizeBytes(file.getSize())
                .s3Key(s3Key)
                .caption(caption)
                .folderPath(folderPath)
                .category(FileMessage.categoryFrom(file.getContentType()))
                .downloadPermission(effectivePerm)
                .build();

        msgRepo.save(msg);

        // Atomically update storage counter (thread-safe, GREATEST(0,..) prevents negatives)
        userRepo.adjustStorageUsed(senderId, fileSize);

        FileMessageResponse response = convService.toMessageResponse(msg);

        // Broadcast via WebSocket to topic (all members subscribe to this)
        messagingTemplate.convertAndSend(
                "/topic/conversation/" + conversationId,
                new FileMessageEvent("NEW_FILE", response));

        // Also notify each non-sender member on their personal channel so the
        // sidebar updates even when they are not currently viewing this conversation.
        memberRepo.findActiveMembers(conversationId).stream()
                .filter(m -> !m.getUser().getId().equals(senderId))
                .forEach(m -> messagingTemplate.convertAndSend(
                        "/topic/user/" + m.getUser().getId() + "/notifications",
                        new NotificationEvent("NEW_FILE", Map.of(
                                "conversationId",   conversationId,
                                "senderName",       sender.getDisplayName(),
                                "fileName",         msg.getOriginalFileName(),
                                "fileMessageId",    msg.getId()
                        ))));

        // ── @mention notifications ────────────────────────────────────────────────
        if (mentionedUserIds != null && !mentionedUserIds.isBlank()) {
            Arrays.stream(mentionedUserIds.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(s -> {
                        try { return Long.parseLong(s); } catch (NumberFormatException e) { return null; }
                    })
                    .filter(id -> id != null && !id.equals(senderId))
                    .forEach(mentionedId ->
                        messagingTemplate.convertAndSend(
                                "/topic/user/" + mentionedId + "/notifications",
                                new NotificationEvent("MENTION", Map.of(
                                        "conversationId", conversationId,
                                        "senderName",     sender.getDisplayName(),
                                        "fileName",       msg.getOriginalFileName(),
                                        "caption",        caption != null ? caption : "",
                                        "fileMessageId",  msg.getId()
                                ))));
        }

        activityService.record("FILE_UPLOADED", senderId, conversationId, msg.getId(), null,
                sender.getDisplayName() + " uploaded \"" + msg.getOriginalFileName() + "\"");

        log.info("File sent: msgId={}, conv={}, sender={}, size={}", msg.getId(), conversationId, senderId, fileSize);
        return response;
    }

    /**
     * Upload an entire folder — processes each file with its relative path.
     * Broadcasts one WebSocket event per file so the UI stays live.
     */
    @Transactional
    public List<FileMessageResponse> sendFolder(Long conversationId, Long senderId,
                                                MultipartFile[] files, String[] relativePaths) {
        return sendFolder(conversationId, senderId, files, relativePaths, null, null);
    }

    @Transactional
    public List<FileMessageResponse> sendFolder(Long conversationId, Long senderId,
                                                MultipartFile[] files, String[] relativePaths,
                                                String caption) {
        return sendFolder(conversationId, senderId, files, relativePaths, caption, null);
    }

    @Transactional
    public List<FileMessageResponse> sendFolder(Long conversationId, Long senderId,
                                                MultipartFile[] files, String[] relativePaths,
                                                String caption,
                                                FileMessage.DownloadPermission downloadPermission) {
        if (!memberRepo.existsByConversationIdAndUserIdAndIsActiveTrue(conversationId, senderId)) {
            throw new AppException(HttpStatus.FORBIDDEN, "You are not a member of this conversation.");
        }
        if (files == null || files.length == 0) {
            throw new AppException(HttpStatus.BAD_REQUEST, "No files provided.");
        }

        // Validate total folder size against remaining storage (live sum)
        User senderCheck = userRepo.findById(senderId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Sender not found."));
        long totalFolderSize = java.util.Arrays.stream(files).mapToLong(MultipartFile::getSize).sum();
        long currentFolderUsed = msgRepo.sumFileSizeByUser(senderId);
        if (currentFolderUsed + totalFolderSize > senderCheck.getMaxStorageBytes()) {
            throw new AppException(HttpStatus.PAYLOAD_TOO_LARGE,
                    "Storage limit exceeded. This folder would exceed your 5 GB free plan limit.");
        }

        List<FileMessageResponse> results = new ArrayList<>();
        for (int i = 0; i < files.length; i++) {
            MultipartFile file = files[i];
            if (file.isEmpty()) continue;

            // Derive folderPath from the relative path: "MyFolder/sub/file.txt" → "MyFolder/sub/"
            String relativePath = (relativePaths != null && i < relativePaths.length)
                    ? relativePaths[i] : null;
            String folderPath = null;
            if (relativePath != null && relativePath.contains("/")) {
                folderPath = relativePath.substring(0, relativePath.lastIndexOf('/') + 1);
            }

            // Always store just the bare filename — browsers sometimes include the
            // relative path inside the Content-Disposition, so strip any prefix.
            String rawName = file.getOriginalFilename();
            String originalFileName = (rawName != null && rawName.contains("/"))
                    ? rawName.substring(rawName.lastIndexOf('/') + 1)
                    : rawName;

            var conv   = convRepo.findById(conversationId)
                    .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Conversation not found."));
            User sender = userRepo.findById(senderId)
                    .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Sender not found."));

            String s3Key = storage.uploadFile(file, conversationId);

            FileMessage.DownloadPermission effectiveFolderPerm =
                    downloadPermission != null ? downloadPermission : FileMessage.DownloadPermission.CAN_DOWNLOAD;

            // Same view-only-chain enforcement as sendFile — once the target
            // folder (or any ancestor) is locked, every file inherits VIEW_ONLY.
            if (folderPath != null && !folderPath.isBlank()) {
                boolean inViewOnlyChain = folderService
                        .findFolderByPath(conversationId, folderPath)
                        .map(folderService::isViewOnlyChain)
                        .orElse(false);
                if (inViewOnlyChain) {
                    effectiveFolderPerm = FileMessage.DownloadPermission.VIEW_ONLY;
                }
            }

            FileMessage msg = FileMessage.builder()
                    .conversation(conv)
                    .sender(sender)
                    .originalFileName(originalFileName)
                    .contentType(file.getContentType())
                    .fileSizeBytes(file.getSize())
                    .s3Key(s3Key)
                    .caption(caption)
                    .folderPath(folderPath)
                    .category(FileMessage.categoryFrom(file.getContentType()))
                    .downloadPermission(effectiveFolderPerm)
                    .build();

            msgRepo.save(msg);
            userRepo.adjustStorageUsed(senderId, file.getSize());
            FileMessageResponse response = convService.toMessageResponse(msg);
            results.add(response);

            messagingTemplate.convertAndSend(
                    "/topic/conversation/" + conversationId,
                    new FileMessageEvent("NEW_FILE", response));

            log.info("Folder file sent: msgId={}, path={}, conv={}, size={}", msg.getId(), relativePath, conversationId, file.getSize());
        }
        // Single summary notification to non-sender members after the whole folder is uploaded
        if (!results.isEmpty()) {
            User folderSender = userRepo.findById(senderId).orElse(null);
            String folderSenderName = folderSender != null ? folderSender.getDisplayName() : "Someone";
            String folderName = (relativePaths != null && relativePaths.length > 0)
                    ? relativePaths[0].split("/")[0] : "a folder";
            int fileCount = results.size();
            memberRepo.findActiveMembers(conversationId).stream()
                    .filter(m -> !m.getUser().getId().equals(senderId))
                    .forEach(m -> messagingTemplate.convertAndSend(
                            "/topic/user/" + m.getUser().getId() + "/notifications",
                            new NotificationEvent("NEW_FILE", Map.of(
                                    "conversationId", conversationId,
                                    "senderName",     folderSenderName,
                                    "fileName",       "📁 " + folderName + " (" + fileCount + " files)"
                            ))));
            activityService.record("FOLDER_UPLOADED", senderId, conversationId, null, null,
                    folderSenderName + " uploaded folder \"" + folderName + "\" (" + fileCount + " files)");
        }

        return results;
    }

    /**
     * Get a short-lived presigned download URL, and increment download counter.
     * Enforces downloadPermission: VIEW_ONLY → always denied; ADMIN_ONLY_DOWNLOAD → only admins.
     */
    @Transactional
    public String getDownloadUrl(Long messageId, Long requesterId) {
        FileMessage msg = getAccessibleMessage(messageId, requesterId);
        enforceDownloadPermission(msg, requesterId);
        msg.setDownloadCount((msg.getDownloadCount() != null ? msg.getDownloadCount() : 0) + 1);
        msgRepo.save(msg);
        return storage.generatePresignedUrl(msg.getS3Key());
    }

    /**
     * Get a presigned URL that opens the file inline in the browser (for preview).
     * Always allowed for any conversation member — VIEW_ONLY files can still be previewed.
     */
    public String getPreviewUrl(Long messageId, Long requesterId) {
        FileMessage msg = getAccessibleMessage(messageId, requesterId);
        return storage.generateInlinePresignedUrl(msg.getS3Key());
    }

    /** Get presigned thumbnail URL (images/videos only) */
    public String getThumbnailUrl(Long messageId, Long requesterId) {
        FileMessage msg = getAccessibleMessage(messageId, requesterId);
        if (msg.getThumbnailKey() == null) {
            throw new AppException(HttpStatus.NOT_FOUND, "No thumbnail available for this file.");
        }
        return storage.generatePresignedUrl(msg.getThumbnailKey());
    }

    // ── Pin / Unpin ───────────────────────────────────────────────────────────

    /** Pin a file for the requesting user. Idempotent. */
    @Transactional
    public FileMessageResponse pinFile(Long messageId, Long requesterId) {
        FileMessage msg = getAccessibleMessage(messageId, requesterId);
        if (!pinRepo.existsByUserIdAndFileMessageId(requesterId, messageId)) {
            User user = userRepo.findById(requesterId)
                    .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "User not found."));
            pinRepo.save(UserFilePin.builder()
                    .user(user)
                    .fileMessage(msg)
                    .build());
            activityService.record("FILE_PINNED", requesterId, msg.getConversation().getId(),
                    messageId, null,
                    user.getDisplayName() + " pinned \"" + msg.getOriginalFileName() + "\"");
        }
        return convService.toMessageResponse(msg, null, requesterId);
    }

    /** Unpin a file for the requesting user. Idempotent. */
    @Transactional
    public FileMessageResponse unpinFile(Long messageId, Long requesterId) {
        FileMessage msg = getAccessibleMessage(messageId, requesterId);
        pinRepo.findByUserIdAndFileMessageId(requesterId, messageId)
                .ifPresent(pinRepo::delete);
        return convService.toMessageResponse(msg, null, requesterId);
    }

    /** List all files pinned by the requesting user in a conversation. */
    public List<FileMessageResponse> getPinnedFiles(Long conversationId, Long requesterId) {
        if (!memberRepo.existsByConversationIdAndUserIdAndIsActiveTrue(conversationId, requesterId)) {
            throw new AppException(HttpStatus.FORBIDDEN, "You are not a member of this conversation.");
        }
        return pinRepo.findPinnedInConversation(requesterId, conversationId)
                .stream()
                .map(p -> convService.toMessageResponse(p.getFileMessage(), null, requesterId))
                .toList();
    }

    // ── Permissions ───────────────────────────────────────────────────────────

    /**
     * Change the download permission on a file.
     * Only the original sender or a conversation admin can do this.
     */
    @Transactional
    public FileMessageResponse setPermission(Long messageId, Long requesterId,
                                             FileMessage.DownloadPermission permission) {
        FileMessage msg = msgRepo.findById(messageId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Message not found."));
        if (Boolean.TRUE.equals(msg.getIsDeleted())) {
            throw new AppException(HttpStatus.GONE, "This file has been deleted.");
        }

        boolean isSender = msg.getSender().getId().equals(requesterId);
        boolean isAdmin  = memberRepo.findByConversationIdAndUserId(
                msg.getConversation().getId(), requesterId)
                .map(m -> m.getRole() == ConversationMember.MemberRole.ADMIN)
                .orElse(false);

        if (!isSender && !isAdmin) {
            throw new AppException(HttpStatus.FORBIDDEN,
                    "Only the file sender or a group admin can change download permissions.");
        }

        msg.setDownloadPermission(permission);
        msgRepo.save(msg);
        log.info("Permission updated: msgId={}, permission={}, by={}", messageId, permission, requesterId);
        return convService.toMessageResponse(msg, null, requesterId);
    }

    // ── Permission enforcement helper ─────────────────────────────────────────

    private void enforceDownloadPermission(FileMessage msg, Long requesterId) {
        FileMessage.DownloadPermission perm = msg.getDownloadPermission();

        // Folder-level view-only chain trumps the file's own permission. This
        // catches files uploaded BEFORE the parent folder was made view-only —
        // they're still inside a locked subtree and must not be downloadable.
        if (msg.getFolderPath() != null && !msg.getFolderPath().isBlank()) {
            boolean folderViewOnly = folderService
                    .findFolderByPath(msg.getConversation().getId(), msg.getFolderPath())
                    .map(folderService::isViewOnlyChain)
                    .orElse(false);
            if (folderViewOnly) {
                throw new AppException(HttpStatus.FORBIDDEN,
                        "This file lives in a view-only folder and cannot be downloaded.");
            }
        }

        if (perm == null) return;  // legacy rows with NULL permission → treat as CAN_DOWNLOAD
        if (perm == FileMessage.DownloadPermission.VIEW_ONLY) {
            throw new AppException(HttpStatus.FORBIDDEN,
                    "This file is view-only and cannot be downloaded.");
        }
        if (perm == FileMessage.DownloadPermission.ADMIN_ONLY_DOWNLOAD) {
            boolean isAdmin = memberRepo.findByConversationIdAndUserId(
                    msg.getConversation().getId(), requesterId)
                    .map(m -> m.getRole() == ConversationMember.MemberRole.ADMIN)
                    .orElse(false);
            if (!isAdmin) {
                throw new AppException(HttpStatus.FORBIDDEN,
                        "Only group admins can download this file.");
            }
        }
    }

    /**
     * Soft-delete a file message (sender or group admin only).
     */
    @Transactional
    public void deleteMessage(Long messageId, Long requesterId) {
        FileMessage msg = msgRepo.findById(messageId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Message not found."));

        boolean isSender = msg.getSender().getId().equals(requesterId);
        boolean isAdmin  = memberRepo.findByConversationIdAndUserId(
                msg.getConversation().getId(), requesterId)
                .map(m -> m.getRole() == com.magizhchi.share.model.ConversationMember.MemberRole.ADMIN)
                .orElse(false);

        if (!isSender && !isAdmin) {
            throw new AppException(HttpStatus.FORBIDDEN,
                    "Only the sender or a group admin can delete this file.");
        }

        msg.setIsDeleted(true);
        msg.setDeletedAt(Instant.now());
        msgRepo.save(msg);

        // Reclaim storage from the original uploader
        userRepo.adjustStorageUsed(msg.getSender().getId(), -msg.getFileSizeBytes());

        // Notify members
        messagingTemplate.convertAndSend(
                "/topic/conversation/" + msg.getConversation().getId(),
                new FileMessageEvent("FILE_DELETED",
                        FileMessageResponse.builder().id(messageId).build()));
    }

    private FileMessage getAccessibleMessage(Long messageId, Long userId) {
        FileMessage msg = msgRepo.findById(messageId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Message not found."));
        if (Boolean.TRUE.equals(msg.getIsDeleted())) {
            throw new AppException(HttpStatus.GONE, "This file has been deleted.");
        }
        // Allow access if user is a conversation member OR the file was shared with them
        boolean isMember = memberRepo.existsByConversationIdAndUserIdAndIsActiveTrue(
                msg.getConversation().getId(), userId);
        boolean isShared = !isMember && sharingService.hasAccess(messageId, userId);
        if (!isMember && !isShared) {
            throw new AppException(HttpStatus.FORBIDDEN, "Access denied.");
        }
        return msg;
    }
}
