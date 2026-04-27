package com.magizhchi.share.service;

import com.magizhchi.share.dto.response.StorageResponse;
import com.magizhchi.share.exception.AppException;
import com.magizhchi.share.model.Conversation.ConversationType;
import com.magizhchi.share.repository.ConversationRepository;
import com.magizhchi.share.repository.FileMessageRepository;
import com.magizhchi.share.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StorageService {

    private final UserRepository         userRepo;
    private final FileMessageRepository  fileRepo;
    private final ConversationRepository convRepo;

    @Transactional(readOnly = true)
    public StorageResponse getUsage(Long userId) {
        var user = userRepo.findById(userId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "User not found."));

        long used  = user.getStorageUsedBytes();
        long limit = user.getMaxStorageBytes();
        double pct = limit > 0
                ? BigDecimal.valueOf(used * 100.0 / limit)
                             .setScale(1, RoundingMode.HALF_UP)
                             .doubleValue()
                : 0.0;

        // Breakdown by conversation type
        long personalBytes = fileRepo.sumByUserAndConvType(userId, ConversationType.PERSONAL);
        long directBytes   = fileRepo.sumByUserAndConvType(userId, ConversationType.DIRECT);
        long groupBytes    = fileRepo.sumByUserAndConvType(userId, ConversationType.GROUP);

        // Per-group breakdown (only groups where user actually uploaded)
        List<StorageResponse.GroupItem> groupItems = convRepo.findGroupsByMember(userId)
                .stream()
                .map(g -> {
                    long bytes = fileRepo.sumByUserInConversation(userId, g.getId());
                    return StorageResponse.GroupItem.builder()
                            .conversationId(g.getId())
                            .name(g.getName())
                            .usedBytes(bytes)
                            .build();
                })
                .filter(gi -> gi.getUsedBytes() > 0)
                .sorted((a, b) -> Long.compare(b.getUsedBytes(), a.getUsedBytes()))
                .toList();

        // Top 10 largest files uploaded by this user
        List<StorageResponse.TopFileItem> topFiles =
                fileRepo.findTopFilesBySize(userId, PageRequest.of(0, 10))
                        .stream()
                        .map(f -> StorageResponse.TopFileItem.builder()
                                .id(f.getId())
                                .fileName(f.getOriginalFileName())
                                .contentType(f.getContentType())
                                .category(f.getCategory().name())
                                .sizeBytes(f.getFileSizeBytes())
                                .sentAt(f.getSentAt())
                                .build())
                        .toList();

        return StorageResponse.builder()
                .usedBytes(used)
                .limitBytes(limit)
                .usedPercent(pct)
                .personalBytes(personalBytes)
                .directBytes(directBytes)
                .groupBytes(groupBytes)
                .groupBreakdown(groupItems)
                .topFiles(topFiles)
                .build();
    }
}
