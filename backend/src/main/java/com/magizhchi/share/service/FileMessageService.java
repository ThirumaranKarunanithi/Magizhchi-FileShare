package com.magizhchi.share.service;

import com.magizhchi.share.dto.response.FileMessageResponse;
import com.magizhchi.share.exception.AppException;
import com.magizhchi.share.model.FileMessage;
import com.magizhchi.share.model.User;
import com.magizhchi.share.repository.ConversationMemberRepository;
import com.magizhchi.share.repository.ConversationRepository;
import com.magizhchi.share.repository.FileMessageRepository;
import com.magizhchi.share.repository.UserRepository;
import com.magizhchi.share.websocket.FileMessageEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

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

    /**
     * Upload a file, create the FileMessage record, and push a WebSocket event
     * to all members of the conversation.
     */
    @Transactional
    public FileMessageResponse sendFile(Long conversationId, Long senderId,
                                        MultipartFile file, String caption) {
        return sendFile(conversationId, senderId, file, caption, null);
    }

    @Transactional
    public FileMessageResponse sendFile(Long conversationId, Long senderId,
                                        MultipartFile file, String caption, String folderPath) {
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

        // Upload to S3
        String s3Key = storage.uploadFile(file, conversationId);

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
                .build();

        msgRepo.save(msg);

        FileMessageResponse response = convService.toMessageResponse(msg);

        // Broadcast via WebSocket to topic (all members subscribe to this)
        messagingTemplate.convertAndSend(
                "/topic/conversation/" + conversationId,
                new FileMessageEvent("NEW_FILE", response));

        log.info("File sent: msgId={}, conv={}, sender={}", msg.getId(), conversationId, senderId);
        return response;
    }

    /**
     * Upload an entire folder — processes each file with its relative path.
     * Broadcasts one WebSocket event per file so the UI stays live.
     */
    @Transactional
    public List<FileMessageResponse> sendFolder(Long conversationId, Long senderId,
                                                MultipartFile[] files, String[] relativePaths) {
        if (!memberRepo.existsByConversationIdAndUserIdAndIsActiveTrue(conversationId, senderId)) {
            throw new AppException(HttpStatus.FORBIDDEN, "You are not a member of this conversation.");
        }
        if (files == null || files.length == 0) {
            throw new AppException(HttpStatus.BAD_REQUEST, "No files provided.");
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

            FileMessage msg = FileMessage.builder()
                    .conversation(conv)
                    .sender(sender)
                    .originalFileName(originalFileName)
                    .contentType(file.getContentType())
                    .fileSizeBytes(file.getSize())
                    .s3Key(s3Key)
                    .folderPath(folderPath)
                    .category(FileMessage.categoryFrom(file.getContentType()))
                    .build();

            msgRepo.save(msg);
            FileMessageResponse response = convService.toMessageResponse(msg);
            results.add(response);

            messagingTemplate.convertAndSend(
                    "/topic/conversation/" + conversationId,
                    new FileMessageEvent("NEW_FILE", response));

            log.info("Folder file sent: msgId={}, path={}, conv={}", msg.getId(), relativePath, conversationId);
        }
        return results;
    }

    /**
     * Get a short-lived presigned download URL, and increment download counter.
     */
    @Transactional
    public String getDownloadUrl(Long messageId, Long requesterId) {
        FileMessage msg = getAccessibleMessage(messageId, requesterId);
        msg.setDownloadCount(msg.getDownloadCount() + 1);
        msgRepo.save(msg);
        return storage.generatePresignedUrl(msg.getS3Key());
    }

    /** Get presigned thumbnail URL (images/videos only) */
    public String getThumbnailUrl(Long messageId, Long requesterId) {
        FileMessage msg = getAccessibleMessage(messageId, requesterId);
        if (msg.getThumbnailKey() == null) {
            throw new AppException(HttpStatus.NOT_FOUND, "No thumbnail available for this file.");
        }
        return storage.generatePresignedUrl(msg.getThumbnailKey());
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

        // Notify members
        messagingTemplate.convertAndSend(
                "/topic/conversation/" + msg.getConversation().getId(),
                new FileMessageEvent("FILE_DELETED",
                        FileMessageResponse.builder().id(messageId).build()));
    }

    private FileMessage getAccessibleMessage(Long messageId, Long userId) {
        FileMessage msg = msgRepo.findById(messageId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Message not found."));
        if (msg.getIsDeleted()) {
            throw new AppException(HttpStatus.GONE, "This file has been deleted.");
        }
        if (!memberRepo.existsByConversationIdAndUserIdAndIsActiveTrue(
                msg.getConversation().getId(), userId)) {
            throw new AppException(HttpStatus.FORBIDDEN, "Access denied.");
        }
        return msg;
    }
}
