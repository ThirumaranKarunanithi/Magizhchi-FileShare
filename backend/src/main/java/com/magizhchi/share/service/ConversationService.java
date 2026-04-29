package com.magizhchi.share.service;

import com.magizhchi.share.dto.request.CreateGroupRequest;
import com.magizhchi.share.dto.response.ConversationResponse;
import com.magizhchi.share.dto.response.FileMessageResponse;
import com.magizhchi.share.dto.response.GroupMemberResponse;
import com.magizhchi.share.exception.AppException;
import com.magizhchi.share.model.*;
import com.magizhchi.share.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class ConversationService {

    private final ConversationRepository       convRepo;
    private final ConversationMemberRepository memberRepo;
    private final FileMessageRepository        msgRepo;
    private final UserRepository               userRepo;
    private final FileStorageService           storage;
    private final ConnectionService            connService;
    private final UserFilePinRepository        pinRepo;

    // ── List ──────────────────────────────────────────────────────────────────

    public List<ConversationResponse> listForUser(Long userId) {
        return convRepo.findAllByMemberUserId(userId)
                .stream()
                .map(c -> toResponse(c, userId))
                .sorted(Comparator.comparing(
                        r -> r.getLastFile() != null ? r.getLastFile().getSentAt() : java.time.Instant.EPOCH,
                        Comparator.reverseOrder()))
                .toList();
    }

    // ── Personal storage ──────────────────────────────────────────────────────

    /**
     * Returns the caller's personal storage conversation, creating it on first access.
     * There is exactly one PERSONAL conversation per user.
     */
    @Transactional
    public ConversationResponse getOrCreatePersonal(Long userId) {
        User user = getUser(userId);
        return convRepo.findPersonalConversation(userId)
                .map(c -> toResponse(c, userId))
                .orElseGet(() -> {
                    Conversation personal = Conversation.builder()
                            .type(Conversation.ConversationType.PERSONAL)
                            .name("My Storage")
                            .createdBy(user)
                            .build();
                    convRepo.save(personal);
                    addMember(personal, user, ConversationMember.MemberRole.ADMIN);
                    return toResponse(personal, userId);
                });
    }

    // ── Direct chat ───────────────────────────────────────────────────────────

    /**
     * Per-user-pair locks for getOrCreateDirect. Without this, two simultaneous
     * "open direct chat" requests (e.g. user clicks twice, or both clients open
     * the chat right after a connection accept) can each pass the
     * findDirectConversation() check and then both insert a row, leaving the
     * pair with two distinct DIRECT conversations.
     *
     * Locks are interned per canonical "minId:maxId" key so both directions
     * share the same lock object.
     */
    private static final ConcurrentHashMap<String, Object> DIRECT_LOCKS = new ConcurrentHashMap<>();

    private static Object directLockFor(Long a, Long b) {
        long lo = Math.min(a, b), hi = Math.max(a, b);
        return DIRECT_LOCKS.computeIfAbsent(lo + ":" + hi, k -> new Object());
    }

    @Transactional
    public ConversationResponse getOrCreateDirect(Long currentUserId, Long targetUserId) {
        if (currentUserId.equals(targetUserId)) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Cannot start a chat with yourself.");
        }

        User currentUser = getUser(currentUserId);
        User targetUser  = getUser(targetUserId);

        // Privacy guard — must be connected and not blocked
        connService.requireConnected(currentUserId, targetUserId);

        // Fast path — already exists, no lock needed.
        var existing = convRepo.findDirectConversation(currentUserId, targetUserId);
        if (existing.isPresent()) return toResponse(existing.get(), currentUserId);

        // Slow path — serialize creation per user-pair so concurrent callers
        // can't both insert. Re-check inside the lock in case another thread
        // created it while we were waiting.
        synchronized (directLockFor(currentUserId, targetUserId)) {
            return convRepo.findDirectConversation(currentUserId, targetUserId)
                    .map(c -> toResponse(c, currentUserId))
                    .orElseGet(() -> {
                        Conversation conv = Conversation.builder()
                                .type(Conversation.ConversationType.DIRECT)
                                .createdBy(currentUser)
                                .build();
                        convRepo.save(conv);
                        addMember(conv, currentUser, ConversationMember.MemberRole.MEMBER);
                        addMember(conv, targetUser,  ConversationMember.MemberRole.MEMBER);
                        return toResponse(conv, currentUserId);
                    });
        }
    }

    // ── Group ─────────────────────────────────────────────────────────────────

    private static final int FREE_PLAN_GROUP_LIMIT = 3;

    @Transactional
    public ConversationResponse createGroup(Long creatorId, CreateGroupRequest req,
                                             MultipartFile iconFile) {
        User creator = getUser(creatorId);

        // Free-plan group limit
        long groupCount = convRepo.countGroupsByCreator(creatorId);
        if (groupCount >= FREE_PLAN_GROUP_LIMIT) {
            throw new AppException(HttpStatus.FORBIDDEN,
                    "Free plan allows a maximum of " + FREE_PLAN_GROUP_LIMIT + " groups. " +
                    "Upgrade your plan to create more groups.");
        }

        Conversation group = Conversation.builder()
                .type(Conversation.ConversationType.GROUP)
                .name(req.getName())
                .createdBy(creator)
                .build();

        if (iconFile != null && !iconFile.isEmpty()) {
            String iconUrl = storage.uploadProfilePhoto(iconFile, creatorId);
            group.setIconUrl(iconUrl);
        }

        convRepo.save(group);
        addMember(group, creator, ConversationMember.MemberRole.ADMIN);

        for (Long memberId : req.getMemberIds()) {
            if (!memberId.equals(creatorId)) {
                userRepo.findById(memberId).ifPresent(u ->
                        addMember(group, u, ConversationMember.MemberRole.MEMBER));
            }
        }

        return toResponse(group, creatorId);
    }

    @Transactional
    public ConversationResponse addMemberToGroup(Long conversationId, Long requesterId, Long newUserId) {
        Conversation conv = getConversation(conversationId);
        requireAdmin(conv, requesterId);
        User newUser = getUser(newUserId);

        // Re-activate if previously removed
        memberRepo.findByConversationIdAndUserId(conversationId, newUserId).ifPresentOrElse(m -> {
            m.setIsActive(true);
            m.setJoinedAt(Instant.now());
            memberRepo.save(m);
        }, () -> addMember(conv, newUser, ConversationMember.MemberRole.MEMBER));

        return toResponse(conv, requesterId);
    }

    @Transactional
    public void removeMemberFromGroup(Long conversationId, Long requesterId, Long targetUserId) {
        Conversation conv = getConversation(conversationId);

        // Admin can remove anyone; members can only remove themselves (self-exit)
        if (!requesterId.equals(targetUserId)) {
            requireAdmin(conv, requesterId);
        }

        ConversationMember member = memberRepo
                .findByConversationIdAndUserId(conversationId, targetUserId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Member not found."));

        boolean leavingUserIsAdmin = member.getRole() == ConversationMember.MemberRole.ADMIN;

        // Deactivate the member
        member.setIsActive(false);
        member.setLeftAt(Instant.now());
        memberRepo.save(member);

        // ── Admin-transfer logic ──────────────────────────────────────────────
        // Only needed when the leaving user was an admin.
        if (leavingUserIsAdmin) {
            List<ConversationMember> remaining = memberRepo.findActiveMembers(conversationId);

            if (remaining.isEmpty()) {
                // Last person left — group is now empty.
                // It won't appear in anyone's sidebar (no active members).
                // Files remain in S3 with storage credited to their respective uploaders.
                return;
            }

            boolean stillHasAdmin = remaining.stream()
                    .anyMatch(m -> m.getRole() == ConversationMember.MemberRole.ADMIN);

            if (!stillHasAdmin) {
                // Auto-promote the longest-standing active member to ADMIN
                // (the one who joined earliest, i.e. minimum joinedAt).
                ConversationMember promoted = remaining.stream()
                        .min(Comparator.comparing(ConversationMember::getJoinedAt))
                        .orElseThrow();
                promoted.setRole(ConversationMember.MemberRole.ADMIN);
                memberRepo.save(promoted);
            }
        }
    }

    // ── Role management ───────────────────────────────────────────────────────

    /**
     * Promote a member to ADMIN or demote an admin back to MEMBER.
     * Only an existing admin can perform this.
     * Demoting the last admin is blocked — the group must always have at least one admin.
     */
    /**
     * Rename a group conversation. Only group admins can do this; DIRECT and
     * PERSONAL conversations don't have a user-facing name to rename.
     *
     * <p>The new name is trimmed and length-clamped to mirror the validation
     * on {@link com.magizhchi.share.dto.request.CreateGroupRequest} so the
     * UPDATE path matches the CREATE path. Whitespace-only names are
     * rejected with a 400.
     */
    @Transactional
    public ConversationResponse renameGroup(Long conversationId, Long requesterId, String newName) {
        Conversation conv = getConversation(conversationId);

        if (conv.getType() != Conversation.ConversationType.GROUP) {
            throw new AppException(HttpStatus.BAD_REQUEST,
                    "Only group conversations can be renamed.");
        }
        requireAdmin(conv, requesterId);

        String trimmed = newName == null ? "" : newName.trim();
        if (trimmed.isEmpty()) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Group name cannot be empty.");
        }
        if (trimmed.length() > 80) {
            throw new AppException(HttpStatus.BAD_REQUEST,
                    "Group name must be 80 characters or fewer.");
        }

        conv.setName(trimmed);
        return toResponse(convRepo.save(conv), requesterId);
    }

    @Transactional
    public void setMemberRole(Long conversationId, Long requesterId,
                              Long targetUserId, ConversationMember.MemberRole newRole) {
        Conversation conv = getConversation(conversationId);
        requireAdmin(conv, requesterId);

        if (requesterId.equals(targetUserId)) {
            throw new AppException(HttpStatus.BAD_REQUEST,
                    "Use 'Exit Group' to leave. You cannot change your own role here.");
        }

        ConversationMember member = memberRepo
                .findByConversationIdAndUserId(conversationId, targetUserId)
                .filter(ConversationMember::getIsActive)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Member not found."));

        // Guard: don't allow demoting the last remaining admin
        if (newRole == ConversationMember.MemberRole.MEMBER
                && member.getRole() == ConversationMember.MemberRole.ADMIN) {
            long adminCount = memberRepo.findActiveMembers(conversationId).stream()
                    .filter(m -> m.getRole() == ConversationMember.MemberRole.ADMIN)
                    .count();
            if (adminCount <= 1) {
                throw new AppException(HttpStatus.BAD_REQUEST,
                        "Cannot demote the only admin. Promote another member first.");
            }
        }

        member.setRole(newRole);
        memberRepo.save(member);
    }

    // ── Members ──────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<GroupMemberResponse> getGroupMembers(Long conversationId, Long requesterId) {
        requireMember(conversationId, requesterId);
        return memberRepo.findActiveMembers(conversationId).stream()
                .map(m -> GroupMemberResponse.builder()
                        .userId(m.getUser().getId())
                        .displayName(m.getUser().getDisplayName())
                        // Re-presign so the avatar URL is always fresh.
                        .profilePhotoUrl(storage.refreshProfilePhotoUrl(m.getUser().getProfilePhotoUrl()))
                        .role(m.getRole().name())
                        .joinedAt(m.getJoinedAt())
                        .build())
                .toList();
    }

    // ── File history ──────────────────────────────────────────────────────────

    public Page<FileMessageResponse> getFileHistory(Long conversationId, Long userId,
                                                     int page, int size) {
        requireMember(conversationId, userId);
        return msgRepo.findByConversationId(conversationId, PageRequest.of(page, size))
                .map(fm -> toMessageResponse(fm, null, userId));
    }

    // ── Details ───────────────────────────────────────────────────────────────

    public ConversationResponse getDetails(Long conversationId, Long userId) {
        requireMember(conversationId, userId);
        return toResponse(getConversation(conversationId), userId);
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private void addMember(Conversation conv, User user, ConversationMember.MemberRole role) {
        ConversationMember m = ConversationMember.builder()
                .conversation(conv)
                .user(user)
                .role(role)
                .build();
        memberRepo.save(m);
    }

    private void requireMember(Long conversationId, Long userId) {
        if (!memberRepo.existsByConversationIdAndUserIdAndIsActiveTrue(conversationId, userId)) {
            throw new AppException(HttpStatus.FORBIDDEN, "You are not a member of this conversation.");
        }
    }

    private void requireAdmin(Conversation conv, Long userId) {
        conv.getMembers().stream()
                .filter(m -> m.getUser().getId().equals(userId) && m.getIsActive())
                .findFirst()
                .filter(m -> m.getRole() == ConversationMember.MemberRole.ADMIN)
                .orElseThrow(() -> new AppException(HttpStatus.FORBIDDEN,
                        "Only group admins can perform this action."));
    }

    private Conversation getConversation(Long id) {
        return convRepo.findById(id)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Conversation not found."));
    }

    private User getUser(Long id) {
        return userRepo.findById(id)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "User not found."));
    }

    ConversationResponse toResponse(Conversation conv, Long currentUserId) {
        FileMessage last = msgRepo.findLatestInConversation(conv.getId()).orElse(null);
        int memberCount = memberRepo.countByConversationIdAndIsActiveTrue(conv.getId());

        String displayName = conv.getName();
        String photoUrl    = conv.getIconUrl();
        Long   otherUserId = null;

        if (conv.getType() == Conversation.ConversationType.DIRECT) {
            var other = conv.getMembers().stream()
                    .filter(m -> m.getIsActive() && !m.getUser().getId().equals(currentUserId))
                    .findFirst();
            if (other.isPresent()) {
                displayName = other.get().getUser().getDisplayName();
                photoUrl    = other.get().getUser().getProfilePhotoUrl();
                otherUserId = other.get().getUser().getId();
            }
        }

        // Re-presign the photo URL — both group icons and DIRECT-chat avatars
        // are stored as presigned URLs that expire. Without this, conversation
        // rows show blank circles after the original presigned link runs out.
        String freshPhoto = storage.refreshProfilePhotoUrl(photoUrl);
        if (freshPhoto != null) photoUrl = freshPhoto;

        return ConversationResponse.builder()
                .id(conv.getId())
                .type(conv.getType().name())
                .name(displayName)
                .iconUrl(photoUrl)
                .memberCount(memberCount)
                .otherUserId(otherUserId)
                .lastFile(last != null ? toMessageResponse(last) : null)
                .createdAt(conv.getCreatedAt())
                .build();
    }

    FileMessageResponse toMessageResponse(FileMessage fm) {
        return toMessageResponse(fm, null, null);
    }

    FileMessageResponse toMessageResponse(FileMessage fm, String conversationName) {
        return toMessageResponse(fm, conversationName, null);
    }

    FileMessageResponse toMessageResponse(FileMessage fm, String conversationName, Long requesterId) {
        boolean pinned = requesterId != null
                && pinRepo.existsByUserIdAndFileMessageId(requesterId, fm.getId());
        return FileMessageResponse.builder()
                .id(fm.getId())
                .conversationId(fm.getConversation().getId())
                .senderId(fm.getSender().getId())
                .senderName(fm.getSender().getDisplayName())
                .senderPhotoUrl(fm.getSender().getProfilePhotoUrl())
                .originalFileName(fm.getOriginalFileName())
                .contentType(fm.getContentType())
                .fileSizeBytes(fm.getFileSizeBytes())
                .category(fm.getCategory().name())
                .caption(fm.getCaption())
                .folderPath(fm.getFolderPath())
                .folderId(fm.getFolder() != null ? fm.getFolder().getId() : null)
                .downloadPermission(fm.getDownloadPermission() != null
                        ? fm.getDownloadPermission().name()
                        : FileMessage.DownloadPermission.CAN_DOWNLOAD.name())
                .isPinned(pinned)
                .hasThumbnail(fm.getThumbnailKey() != null)
                .conversationName(conversationName)
                .sentAt(fm.getSentAt())
                .build();
    }

    /**
     * Search files across all conversations the user is a member of.
     * Matches on filename or caption (case-insensitive).
     * Returns at most 30 results, newest first.
     */
    @Transactional(readOnly = true)
    public List<FileMessageResponse> searchFiles(Long userId, String q) {
        if (q == null || q.isBlank()) return List.of();
        return msgRepo.searchForUser(userId, q.trim(), PageRequest.of(0, 30))
                .stream()
                .map(fm -> {
                    // Resolve a human-friendly conversation name for DIRECT convs
                    Conversation conv = fm.getConversation();
                    String convName;
                    if (conv.getType() == Conversation.ConversationType.DIRECT) {
                        convName = conv.getMembers().stream()
                                .filter(m -> m.getIsActive() && !m.getUser().getId().equals(userId))
                                .findFirst()
                                .map(m -> m.getUser().getDisplayName())
                                .orElse(conv.getName());
                    } else {
                        convName = conv.getName();
                    }
                    return toMessageResponse(fm, convName);
                })
                .toList();
    }
}
