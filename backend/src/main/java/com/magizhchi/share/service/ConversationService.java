package com.magizhchi.share.service;

import com.magizhchi.share.dto.request.CreateGroupRequest;
import com.magizhchi.share.dto.response.ConversationResponse;
import com.magizhchi.share.dto.response.FileMessageResponse;
import com.magizhchi.share.dto.response.GroupMemberResponse;
import com.magizhchi.share.exception.AppException;
import com.magizhchi.share.model.*;
import com.magizhchi.share.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ConversationService {

    private final ConversationRepository       convRepo;
    private final ConversationMemberRepository memberRepo;
    private final FileMessageRepository        msgRepo;
    private final UserRepository               userRepo;
    private final FileStorageService           storage;
    private final ConnectionService            connService;

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

    @Transactional
    public ConversationResponse getOrCreateDirect(Long currentUserId, Long targetUserId) {
        if (currentUserId.equals(targetUserId)) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Cannot start a chat with yourself.");
        }

        User currentUser = getUser(currentUserId);
        User targetUser  = getUser(targetUserId);

        // Privacy guard — must be connected and not blocked
        connService.requireConnected(currentUserId, targetUserId);

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

        // Admin can remove anyone; members can only remove themselves
        if (!requesterId.equals(targetUserId)) {
            requireAdmin(conv, requesterId);
        }

        ConversationMember member = memberRepo
                .findByConversationIdAndUserId(conversationId, targetUserId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Member not found."));

        member.setIsActive(false);
        member.setLeftAt(Instant.now());
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
                        .profilePhotoUrl(m.getUser().getProfilePhotoUrl())
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
                .map(this::toMessageResponse);
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
                .hasThumbnail(fm.getThumbnailKey() != null)
                .sentAt(fm.getSentAt())
                .build();
    }
}
