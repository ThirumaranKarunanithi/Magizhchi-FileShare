package com.magizhchi.share.controller;

import com.magizhchi.share.dto.response.ActivityEventResponse;
import com.magizhchi.share.model.User;
import com.magizhchi.share.repository.ConversationMemberRepository;
import com.magizhchi.share.service.ActivityService;
import com.magizhchi.share.exception.AppException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * GET /api/activity?conversationId=&limit= — recent activity events for a conversation
 */
@RestController
@RequestMapping("/api/activity")
@RequiredArgsConstructor
public class ActivityController {

    private final ActivityService              activityService;
    private final ConversationMemberRepository memberRepo;

    /**
     * Returns the most recent activity events for a conversation.
     * Caller must be an active member.
     *
     * @param conversationId  which conversation's feed to fetch
     * @param limit           max events to return (default 20, max 50)
     */
    @GetMapping
    public ResponseEntity<List<ActivityEventResponse>> getActivity(
            @RequestParam Long conversationId,
            @RequestParam(defaultValue = "20") int limit,
            @AuthenticationPrincipal User user) {

        if (!memberRepo.existsByConversationIdAndUserIdAndIsActiveTrue(conversationId, user.getId())) {
            throw new AppException(HttpStatus.FORBIDDEN, "You are not a member of this conversation.");
        }
        return ResponseEntity.ok(activityService.getActivity(conversationId, limit));
    }
}
