package com.magizhchi.share.service;

import com.magizhchi.share.dto.request.ShareRequest;
import com.magizhchi.share.dto.response.SharedResourceResponse;
import com.magizhchi.share.exception.AppException;
import com.magizhchi.share.model.*;
import com.magizhchi.share.repository.*;
import com.magizhchi.share.websocket.NotificationEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class SharingService {

    private final SharedResourceRepository shareRepo;
    private final FileMessageRepository    fileRepo;
    private final UserRepository           userRepo;
    private final ConversationRepository   convRepo;
    private final ConversationMemberRepository memberRepo;
    private final SimpMessagingTemplate    messaging;

    // ── Share ─────────────────────────────────────────────────────────────────

    @Transactional
    public List<SharedResourceResponse> shareResources(Long ownerId, ShareRequest req) {
        User owner = getUser(ownerId);

        SharedResource.ShareType shareType;
        try {
            shareType = SharedResource.ShareType.valueOf(req.getShareType().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new AppException(HttpStatus.BAD_REQUEST, "shareType must be USER or GROUP");
        }

        SharedResource.Permission permission;
        try {
            permission = SharedResource.Permission.valueOf(req.getPermission().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new AppException(HttpStatus.BAD_REQUEST, "permission must be VIEWER or EDITOR");
        }

        // Resolve target
        User targetUser = null;
        Conversation targetGroup = null;
        String targetName;
        String targetPhotoUrl = null;

        if (shareType == SharedResource.ShareType.USER) {
            targetUser = getUser(req.getTargetId());
            if (targetUser.getId().equals(ownerId)) {
                throw new AppException(HttpStatus.BAD_REQUEST, "You cannot share files with yourself.");
            }
            targetName     = targetUser.getDisplayName();
            targetPhotoUrl = targetUser.getProfilePhotoUrl();
        } else {
            targetGroup = convRepo.findById(req.getTargetId())
                    .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Group not found."));
            if (targetGroup.getType() != Conversation.ConversationType.GROUP) {
                throw new AppException(HttpStatus.BAD_REQUEST, "Target must be a GROUP conversation.");
            }
            targetName = targetGroup.getName();
        }

        List<SharedResourceResponse> results = new ArrayList<>();

        for (Long fileId : req.getResourceIds()) {
            FileMessage file = fileRepo.findById(fileId)
                    .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND,
                            "File " + fileId + " not found."));

            // Only the original uploader can share
            if (!file.getSender().getId().equals(ownerId)) {
                throw new AppException(HttpStatus.FORBIDDEN,
                        "You can only share files you uploaded.");
            }

            if (file.getIsDeleted()) {
                throw new AppException(HttpStatus.GONE,
                        "File '" + file.getOriginalFileName() + "' has been deleted.");
            }

            // Prevent duplicates — if already shared, update permission instead
            SharedResource existing = null;
            if (shareType == SharedResource.ShareType.USER) {
                existing = shareRepo.findByFileMessageIdAndTargetUserIdAndRevokedFalse(
                        fileId, targetUser.getId()).orElse(null);
            } else {
                existing = shareRepo.findByFileMessageIdAndTargetGroupIdAndRevokedFalse(
                        fileId, targetGroup.getId()).orElse(null);
            }

            SharedResource sr;
            if (existing != null) {
                // Update permission
                existing.setPermission(permission);
                sr = shareRepo.save(existing);
                log.info("Updated share: srId={}, file={}, permission={}", sr.getId(), fileId, permission);
            } else {
                sr = SharedResource.builder()
                        .fileMessage(file)
                        .owner(owner)
                        .shareType(shareType)
                        .targetUser(targetUser)
                        .targetGroup(targetGroup)
                        .permission(permission)
                        .build();
                sr = shareRepo.save(sr);
                log.info("New share: srId={}, file={}, target={}, type={}", sr.getId(), fileId, req.getTargetId(), shareType);
            }

            results.add(toResponse(sr));
        }

        // ── Real-time notification ──────────────────────────────────────────
        if (shareType == SharedResource.ShareType.USER && targetUser != null) {
            messaging.convertAndSend(
                    "/topic/user/" + targetUser.getId() + "/notifications",
                    new NotificationEvent("FILE_SHARED", Map.of(
                            "senderName",  owner.getDisplayName(),
                            "fileCount",   results.size(),
                            "fileName",    results.get(0).getFileName()
                    )));
        } else if (shareType == SharedResource.ShareType.GROUP && targetGroup != null) {
            // Notify all group members except the owner
            final Conversation group = targetGroup;
            memberRepo.findActiveMembers(group.getId()).stream()
                    .filter(m -> !m.getUser().getId().equals(ownerId))
                    .forEach(m -> messaging.convertAndSend(
                            "/topic/user/" + m.getUser().getId() + "/notifications",
                            new NotificationEvent("FILE_SHARED", Map.of(
                                    "senderName", owner.getDisplayName(),
                                    "groupName",  group.getName(),
                                    "fileCount",  results.size()
                            ))));
        }

        return results;
    }

    // ── Shared with me ────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<SharedResourceResponse> getSharedWithMe(Long userId) {
        Instant now = Instant.now();
        List<SharedResource> direct = shareRepo.findDirectSharesWithUser(userId, now);
        List<SharedResource> viaGroup = shareRepo.findGroupSharesWithUser(userId, now);

        List<SharedResource> all = new ArrayList<>(direct);
        all.addAll(viaGroup);

        return all.stream()
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .map(this::toResponse)
                .toList();
    }

    // ── Shared by me ──────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<SharedResourceResponse> getSharedByMe(Long userId) {
        return shareRepo.findSharedByUser(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    // ── Revoke ────────────────────────────────────────────────────────────────

    @Transactional
    public void revokeShare(Long shareId, Long requesterId) {
        SharedResource sr = shareRepo.findById(shareId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Share not found."));

        if (!sr.getOwner().getId().equals(requesterId)) {
            throw new AppException(HttpStatus.FORBIDDEN, "Only the owner can revoke a share.");
        }

        sr.setRevoked(true);
        sr.setRevokedAt(Instant.now());
        shareRepo.save(sr);
        log.info("Share revoked: srId={}, by={}", shareId, requesterId);
    }

    // ── Access check ──────────────────────────────────────────────────────────

    public boolean hasAccess(Long fileMessageId, Long userId) {
        return shareRepo.hasAccess(fileMessageId, userId, Instant.now());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private SharedResourceResponse toResponse(SharedResource sr) {
        FileMessage fm = sr.getFileMessage();

        Long   targetId        = null;
        String targetName      = null;
        String targetPhotoUrl  = null;

        if (sr.getShareType() == SharedResource.ShareType.USER && sr.getTargetUser() != null) {
            targetId       = sr.getTargetUser().getId();
            targetName     = sr.getTargetUser().getDisplayName();
            targetPhotoUrl = sr.getTargetUser().getProfilePhotoUrl();
        } else if (sr.getTargetGroup() != null) {
            targetId   = sr.getTargetGroup().getId();
            targetName = sr.getTargetGroup().getName();
        }

        return SharedResourceResponse.builder()
                .id(sr.getId())
                .fileMessageId(fm.getId())
                .fileName(fm.getOriginalFileName())
                .contentType(fm.getContentType())
                .category(fm.getCategory().name())
                .sizeBytes(fm.getFileSizeBytes())
                .folderPath(fm.getFolderPath())
                .fileSentAt(fm.getSentAt())
                .shareType(sr.getShareType().name())
                .permission(sr.getPermission().name())
                .sharedAt(sr.getCreatedAt())
                .expiresAt(sr.getExpiresAt())
                .ownerId(sr.getOwner().getId())
                .ownerName(sr.getOwner().getDisplayName())
                .ownerPhotoUrl(sr.getOwner().getProfilePhotoUrl())
                .targetId(targetId)
                .targetName(targetName)
                .targetPhotoUrl(targetPhotoUrl)
                .build();
    }

    private User getUser(Long id) {
        return userRepo.findById(id)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "User not found."));
    }
}
