package com.magizhchi.share.service;

import com.magizhchi.share.dto.response.ConnectionRequestResponse;
import com.magizhchi.share.dto.response.UserSearchResponse;
import com.magizhchi.share.exception.AppException;
import com.magizhchi.share.model.Block;
import com.magizhchi.share.model.ConnectionRequest;
import com.magizhchi.share.model.ConnectionRequest.RequestStatus;
import com.magizhchi.share.model.User;
import com.magizhchi.share.repository.BlockRepository;
import com.magizhchi.share.repository.ConnectionRequestRepository;
import com.magizhchi.share.repository.UserRepository;
import com.magizhchi.share.websocket.NotificationEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class ConnectionService {

    private final ConnectionRequestRepository crRepo;
    private final BlockRepository             blockRepo;
    private final UserRepository              userRepo;
    private final SimpMessagingTemplate       messaging;
    private final FileStorageService          fileStorage;

    @Value("${app.connection.daily-request-limit:15}")
    private int dailyRequestLimit;

    // ─────────────────────────────────────────────────────────────────────────
    // Connection Requests
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional
    public ConnectionRequestResponse sendRequest(Long senderId, Long receiverId) {

        if (senderId.equals(receiverId)) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Cannot send a request to yourself.");
        }

        User sender   = getUser(senderId);
        User receiver = getUser(receiverId);

        // Block check — either direction
        if (blockRepo.isBlockedEitherWay(senderId, receiverId)) {
            throw new AppException(HttpStatus.FORBIDDEN,
                    "Action not allowed due to a block between these accounts.");
        }

        // Already connected?
        if (crRepo.isConnected(senderId, receiverId)) {
            throw new AppException(HttpStatus.CONFLICT, "You are already connected with this user.");
        }

        // Existing pending request between the pair?
        List<ConnectionRequest> existing = crRepo.findLatestByPair(senderId, receiverId);
        if (!existing.isEmpty()) {
            ConnectionRequest latest = existing.get(0);
            if (latest.getStatus() == RequestStatus.PENDING) {
                // If the OTHER person already sent us a request, auto-accept it
                if (latest.getReceiver().getId().equals(senderId)) {
                    return acceptRequest(latest.getId(), senderId);
                }
                throw new AppException(HttpStatus.CONFLICT, "A pending request already exists.");
            }
        }

        // Rate limit: max N requests per 24 hours
        long sentToday = crRepo.countRequestsSince(senderId, Instant.now().minus(1, ChronoUnit.DAYS));
        if (sentToday >= dailyRequestLimit) {
            throw new AppException(HttpStatus.TOO_MANY_REQUESTS,
                    "Daily connection request limit (" + dailyRequestLimit + ") reached.");
        }

        ConnectionRequest req = ConnectionRequest.builder()
                .sender(sender)
                .receiver(receiver)
                .build();
        crRepo.save(req);

        // Real-time notification to receiver
        messaging.convertAndSend(
                "/topic/user/" + receiverId + "/notifications",
                new NotificationEvent("CONNECTION_REQUEST", toResponse(req)));

        log.info("Connection request sent: {} → {}", senderId, receiverId);
        return toResponse(req);
    }

    @Transactional
    public ConnectionRequestResponse acceptRequest(Long requestId, Long receiverId) {
        ConnectionRequest req = getRequest(requestId);

        if (!req.getReceiver().getId().equals(receiverId)) {
            throw new AppException(HttpStatus.FORBIDDEN, "Not your request to accept.");
        }
        if (req.getStatus() != RequestStatus.PENDING) {
            throw new AppException(HttpStatus.CONFLICT,
                    "Request is no longer pending (status: " + req.getStatus() + ").");
        }

        req.setStatus(RequestStatus.ACCEPTED);
        req.setRespondedAt(Instant.now());
        crRepo.save(req);

        // Notify the original sender
        messaging.convertAndSend(
                "/topic/user/" + req.getSender().getId() + "/notifications",
                new NotificationEvent("CONNECTION_ACCEPTED", toResponse(req)));

        log.info("Connection accepted: {} ← {}", req.getSender().getId(), receiverId);
        return toResponse(req);
    }

    @Transactional
    public ConnectionRequestResponse rejectRequest(Long requestId, Long receiverId) {
        ConnectionRequest req = getRequest(requestId);

        if (!req.getReceiver().getId().equals(receiverId)) {
            throw new AppException(HttpStatus.FORBIDDEN, "Not your request to reject.");
        }
        if (req.getStatus() != RequestStatus.PENDING) {
            throw new AppException(HttpStatus.CONFLICT, "Request is not pending.");
        }

        req.setStatus(RequestStatus.REJECTED);
        req.setRespondedAt(Instant.now());
        crRepo.save(req);
        log.info("Connection rejected: {} → {}", req.getSender().getId(), receiverId);
        return toResponse(req);
    }

    @Transactional
    public void cancelRequest(Long requestId, Long senderId) {
        ConnectionRequest req = getRequest(requestId);

        if (!req.getSender().getId().equals(senderId)) {
            throw new AppException(HttpStatus.FORBIDDEN, "Not your request to cancel.");
        }
        if (req.getStatus() != RequestStatus.PENDING) {
            throw new AppException(HttpStatus.CONFLICT, "Request is not pending.");
        }

        req.setStatus(RequestStatus.CANCELLED);
        req.setRespondedAt(Instant.now());
        crRepo.save(req);
        log.info("Connection request cancelled by sender {}", senderId);
    }

    public List<ConnectionRequestResponse> getReceivedRequests(Long userId) {
        return crRepo.findByReceiverIdAndStatusOrderByCreatedAtDesc(userId, RequestStatus.PENDING)
                .stream().map(this::toResponse).toList();
    }

    public List<ConnectionRequestResponse> getSentRequests(Long userId) {
        return crRepo.findBySenderIdAndStatusOrderByCreatedAtDesc(userId, RequestStatus.PENDING)
                .stream().map(this::toResponse).toList();
    }

    @Transactional
    public void unfriend(Long userId, Long otherUserId) {
        if (userId.equals(otherUserId)) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Cannot unfriend yourself.");
        }
        List<ConnectionRequest> history = crRepo.findLatestByPair(userId, otherUserId);
        ConnectionRequest accepted = history.stream()
                .filter(r -> r.getStatus() == RequestStatus.ACCEPTED)
                .findFirst()
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND,
                        "No active connection found between these users."));

        accepted.setStatus(RequestStatus.CANCELLED);
        accepted.setRespondedAt(Instant.now());
        crRepo.save(accepted);
        log.info("Unfriended: {} <-> {}", userId, otherUserId);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Blocking
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional
    public void blockUser(Long blockerId, Long blockedId) {
        if (blockerId.equals(blockedId)) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Cannot block yourself.");
        }
        if (blockRepo.existsByBlockerIdAndBlockedId(blockerId, blockedId)) {
            throw new AppException(HttpStatus.CONFLICT, "User is already blocked.");
        }

        User blocker = getUser(blockerId);
        User blocked = getUser(blockedId);

        blockRepo.save(Block.builder().blocker(blocker).blocked(blocked).build());
        log.info("User {} blocked user {}", blockerId, blockedId);
    }

    @Transactional
    public void unblockUser(Long blockerId, Long blockedId) {
        Block block = blockRepo.findByBlockerIdAndBlockedId(blockerId, blockedId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Block not found."));
        blockRepo.delete(block);
        log.info("User {} unblocked user {}", blockerId, blockedId);
    }

    public List<UserSearchResponse> getBlockedUsers(Long blockerId) {
        return blockRepo.findByBlockerIdOrderByCreatedAtDesc(blockerId)
                .stream()
                .map(b -> UserSearchResponse.builder()
                        .id(b.getBlocked().getId())
                        .displayName(b.getBlocked().getDisplayName())
                        .profilePhotoUrl(fileStorage.refreshProfilePhotoUrl(b.getBlocked().getProfilePhotoUrl()))
                        .connectionStatus("BLOCKED_BY_ME")
                        .build())
                .toList();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Search enrichment
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Converts a raw User list from the DB into UserSearchResponse,
     * enriching each entry with the caller's connection status.
     * Hides users who have blocked the caller or whom the caller has blocked.
     */
    public List<UserSearchResponse> enrichSearchResults(List<User> raw, Long viewerId) {
        // Build the combined exclusion set once (blocked in either direction)
        Set<Long> blockedByMe  = blockRepo.findBlockedIdsByBlocker(viewerId);
        Set<Long> blockedMe    = blockRepo.findBlockerIdsByBlocked(viewerId);

        return raw.stream()
                .filter(u -> !blockedByMe.contains(u.getId()))
                .filter(u -> !blockedMe.contains(u.getId()))
                .map(u -> buildSearchResponse(u, viewerId, blockedByMe))
                .toList();
    }

    private UserSearchResponse buildSearchResponse(User target, Long viewerId,
                                                    Set<Long> blockedByMe) {
        if (target.getId().equals(viewerId)) {
            return fullResponse(target, "SELF", null);
        }

        if (blockedByMe.contains(target.getId())) {
            return limitedResponse(target, "BLOCKED_BY_ME", null);
        }

        // Check connection request state
        List<ConnectionRequest> history = crRepo.findLatestByPair(viewerId, target.getId());
        if (!history.isEmpty()) {
            ConnectionRequest latest = history.get(0);
            switch (latest.getStatus()) {
                case ACCEPTED:
                    return fullResponse(target, "CONNECTED", null);
                case PENDING:
                    if (latest.getSender().getId().equals(viewerId)) {
                        return limitedResponse(target, "PENDING_SENT", latest.getId());
                    } else {
                        return limitedResponse(target, "PENDING_RECEIVED", latest.getId());
                    }
                default:
                    break;
            }
        }

        return limitedResponse(target, "NONE", null);
    }

    /** Full profile — shown to self and connected users. */
    private UserSearchResponse fullResponse(User u, String status, Long reqId) {
        return UserSearchResponse.builder()
                .id(u.getId())
                .displayName(u.getDisplayName())
                .profilePhotoUrl(fileStorage.refreshProfilePhotoUrl(u.getProfilePhotoUrl()))
                .mobileNumber(u.getMobileNumber())
                .statusMessage(u.getStatusMessage())
                .connectionStatus(status)
                .connectionRequestId(reqId)
                .build();
    }

    /** Limited profile — shown before a connection is established. */
    private UserSearchResponse limitedResponse(User u, String status, Long reqId) {
        return UserSearchResponse.builder()
                .id(u.getId())
                .displayName(u.getDisplayName())
                .profilePhotoUrl(fileStorage.refreshProfilePhotoUrl(u.getProfilePhotoUrl()))
                // mobileNumber and statusMessage intentionally omitted
                .connectionStatus(status)
                .connectionRequestId(reqId)
                .build();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Access guards (called by ConversationService / FileMessageService)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Throws FORBIDDEN if the two users are not connected or are blocked.
     * Call this before creating a direct conversation or sending a file.
     */
    public void requireConnected(Long userId1, Long userId2) {
        if (blockRepo.isBlockedEitherWay(userId1, userId2)) {
            throw new AppException(HttpStatus.FORBIDDEN,
                    "Action not allowed — a block exists between these accounts.");
        }
        if (!crRepo.isConnected(userId1, userId2)) {
            throw new AppException(HttpStatus.FORBIDDEN,
                    "You must be connected before sharing files. Send a connection request first.");
        }
    }

    /** Non-throwing check used for soft guards. */
    public boolean isConnected(Long u1, Long u2) {
        return crRepo.isConnected(u1, u2);
    }

    public boolean isBlockedEitherWay(Long u1, Long u2) {
        return blockRepo.isBlockedEitherWay(u1, u2);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private ConnectionRequest getRequest(Long id) {
        return crRepo.findById(id)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND,
                        "Connection request not found."));
    }

    private User getUser(Long id) {
        return userRepo.findById(id)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "User not found."));
    }

    ConnectionRequestResponse toResponse(ConnectionRequest r) {
        return ConnectionRequestResponse.builder()
                .id(r.getId())
                .senderId(r.getSender().getId())
                .senderName(r.getSender().getDisplayName())
                .senderPhotoUrl(r.getSender().getProfilePhotoUrl())
                .receiverId(r.getReceiver().getId())
                .receiverName(r.getReceiver().getDisplayName())
                .receiverPhotoUrl(r.getReceiver().getProfilePhotoUrl())
                .status(r.getStatus().name())
                .createdAt(r.getCreatedAt())
                .respondedAt(r.getRespondedAt())
                .build();
    }
}
