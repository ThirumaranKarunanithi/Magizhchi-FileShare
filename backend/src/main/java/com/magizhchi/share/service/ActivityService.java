package com.magizhchi.share.service;

import com.magizhchi.share.dto.response.ActivityEventResponse;
import com.magizhchi.share.model.ActivityEvent;
import com.magizhchi.share.repository.ActivityEventRepository;
import com.magizhchi.share.repository.ConversationRepository;
import com.magizhchi.share.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ActivityService {

    private final ActivityEventRepository activityRepo;
    private final UserRepository          userRepo;
    private final ConversationRepository  convRepo;

    public void record(String eventType, Long actorId, Long conversationId,
                       Long fileMessageId, Long folderId, String description) {
        try {
            var actor = userRepo.findById(actorId).orElse(null);
            var conv  = conversationId != null ? convRepo.findById(conversationId).orElse(null) : null;

            ActivityEvent event = ActivityEvent.builder()
                    .eventType(eventType)
                    .actor(actor)
                    .conversation(conv)
                    .fileMessageId(fileMessageId)
                    .folderId(folderId)
                    .description(description)
                    .build();
            activityRepo.save(event);
        } catch (Exception e) {
            // Activity recording is non-critical — never fail the main flow
            log.warn("Failed to record activity event [{}]: {}", eventType, e.getMessage());
        }
    }

    public List<ActivityEventResponse> getActivity(Long conversationId, int limit) {
        return activityRepo.findByConversationIdOrderByCreatedAtDesc(
                        conversationId, PageRequest.of(0, Math.min(limit, 50)))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private ActivityEventResponse toResponse(ActivityEvent e) {
        return ActivityEventResponse.builder()
                .id(e.getId())
                .eventType(e.getEventType())
                .actorId(e.getActor() != null ? e.getActor().getId() : null)
                .actorName(e.getActor() != null ? e.getActor().getDisplayName() : "Unknown")
                .actorPhotoUrl(e.getActor() != null ? e.getActor().getProfilePhotoUrl() : null)
                .description(e.getDescription())
                .conversationId(e.getConversation() != null ? e.getConversation().getId() : null)
                .fileMessageId(e.getFileMessageId())
                .folderId(e.getFolderId())
                .createdAt(e.getCreatedAt())
                .build();
    }
}
