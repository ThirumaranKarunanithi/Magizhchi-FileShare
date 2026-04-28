package com.magizhchi.share.service;

import com.magizhchi.share.dto.request.CreateFolderRequest;
import com.magizhchi.share.dto.response.FolderResponse;
import com.magizhchi.share.exception.AppException;
import com.magizhchi.share.model.FileMessage;
import com.magizhchi.share.model.Folder;
import com.magizhchi.share.repository.ConversationMemberRepository;
import com.magizhchi.share.repository.ConversationRepository;
import com.magizhchi.share.model.User;
import com.magizhchi.share.model.UserFolderPin;
import com.magizhchi.share.repository.FolderRepository;
import com.magizhchi.share.repository.UserFolderPinRepository;
import com.magizhchi.share.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class FolderService {

    private final FolderRepository              folderRepo;
    private final ConversationRepository        convRepo;
    private final ConversationMemberRepository  memberRepo;
    private final UserRepository                userRepo;
    private final UserFolderPinRepository       folderPinRepo;
    private final ActivityService               activityService;

    @Transactional
    public FolderResponse createFolder(CreateFolderRequest req, Long userId) {
        requireMember(req.getConversationId(), userId);
        var conv = convRepo.findById(req.getConversationId())
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Conversation not found."));
        var creator = userRepo.findById(userId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "User not found."));

        Folder parent = null;
        if (req.getParentFolderId() != null) {
            parent = folderRepo.findById(req.getParentFolderId())
                    .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Parent folder not found."));
        }

        FileMessage.DownloadPermission perm = parsePermission(req.getDefaultPermission());

        Folder folder = Folder.builder()
                .name(req.getName())
                .conversation(conv)
                .createdBy(creator)
                .parent(parent)
                .defaultPermission(perm)
                .build();
        folderRepo.save(folder);

        activityService.record("FOLDER_CREATED", userId, req.getConversationId(),
                null, folder.getId(),
                creator.getDisplayName() + " created folder \"" + req.getName() + "\"");

        log.info("Folder created: id={}, name={}, conv={}", folder.getId(), folder.getName(), req.getConversationId());
        return toResponse(folder, userId);
    }

    public List<FolderResponse> listFolders(Long conversationId, Long parentFolderId, Long userId) {
        requireMember(conversationId, userId);
        List<Folder> folders = parentFolderId == null
                ? folderRepo.findRootFolders(conversationId)
                : folderRepo.findChildFolders(conversationId, parentFolderId);
        Set<Long> pinned = folderPinRepo.findPinnedFolderIdsByConversation(userId, conversationId);
        return folders.stream().map(f -> toResponse(f, pinned.contains(f.getId()))).toList();
    }

    /** Flat listing of every non-deleted folder in the conversation (any depth). */
    public List<FolderResponse> listAllFolders(Long conversationId, Long userId) {
        requireMember(conversationId, userId);
        Set<Long> pinned = folderPinRepo.findPinnedFolderIdsByConversation(userId, conversationId);
        return folderRepo.findAllByConversationId(conversationId)
                .stream()
                .map(f -> toResponse(f, pinned.contains(f.getId())))
                .toList();
    }

    public List<FolderResponse> getBreadcrumb(Long folderId, Long userId) {
        Folder folder = folderRepo.findById(folderId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Folder not found."));
        requireMember(folder.getConversation().getId(), userId);

        List<Folder> path = new ArrayList<>();
        Folder current = folder;
        while (current != null) {
            path.add(0, current);
            current = current.getParent();
        }
        return path.stream().map(f -> toResponse(f, userId)).toList();
    }

    // ── Pin / unpin (per user) ────────────────────────────────────────────────

    @Transactional
    public FolderResponse pinFolder(Long folderId, Long userId) {
        Folder folder = folderRepo.findById(folderId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Folder not found."));
        requireMember(folder.getConversation().getId(), userId);
        if (!folderPinRepo.existsByUserIdAndFolderId(userId, folderId)) {
            User user = userRepo.findById(userId)
                    .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "User not found."));
            folderPinRepo.save(UserFolderPin.builder()
                    .user(user)
                    .folder(folder)
                    .build());
        }
        return toResponse(folder, true);
    }

    @Transactional
    public FolderResponse unpinFolder(Long folderId, Long userId) {
        Folder folder = folderRepo.findById(folderId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Folder not found."));
        requireMember(folder.getConversation().getId(), userId);
        folderPinRepo.findByUserIdAndFolderId(userId, folderId)
                .ifPresent(folderPinRepo::delete);
        return toResponse(folder, false);
    }

    @Transactional
    public FolderResponse renameFolder(Long folderId, String newName, Long userId) {
        Folder folder = getOwnedFolder(folderId, userId);
        folder.setName(newName);
        folderRepo.save(folder);
        return toResponse(folder, userId);
    }

    @Transactional
    public void deleteFolder(Long folderId, Long userId) {
        Folder folder = getOwnedFolder(folderId, userId);
        softDeleteRecursive(folder);
        activityService.record("FOLDER_DELETED", userId, folder.getConversation().getId(),
                null, folderId,
                userRepo.findById(userId).map(u -> u.getDisplayName()).orElse("Someone")
                        + " deleted folder \"" + folder.getName() + "\"");
    }

    private void softDeleteRecursive(Folder folder) {
        folder.setIsDeleted(true);
        folderRepo.save(folder);
        // recursively delete children
        folderRepo.findChildFolders(folder.getConversation().getId(), folder.getId())
                .forEach(this::softDeleteRecursive);
    }

    private Folder getOwnedFolder(Long folderId, Long userId) {
        Folder folder = folderRepo.findById(folderId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Folder not found."));
        requireMember(folder.getConversation().getId(), userId);
        return folder;
    }

    private void requireMember(Long conversationId, Long userId) {
        if (!memberRepo.existsByConversationIdAndUserIdAndIsActiveTrue(conversationId, userId)) {
            throw new AppException(HttpStatus.FORBIDDEN, "You are not a member of this conversation.");
        }
    }

    /** Single-folder response — looks up pin status for the given user. */
    public FolderResponse toResponse(Folder f, Long userId) {
        boolean pinned = userId != null
                && folderPinRepo.existsByUserIdAndFolderId(userId, f.getId());
        return toResponse(f, pinned);
    }

    /** Bulk-friendly response — caller passes the pinned flag (avoids N+1). */
    public FolderResponse toResponse(Folder f, boolean pinned) {
        return FolderResponse.builder()
                .id(f.getId())
                .name(f.getName())
                .parentId(f.getParent() != null ? f.getParent().getId() : null)
                .conversationId(f.getConversation().getId())
                .createdById(f.getCreatedBy().getId())
                .createdByName(f.getCreatedBy().getDisplayName())
                .createdAt(f.getCreatedAt())
                .defaultPermission(f.getDefaultPermission() != null
                        ? f.getDefaultPermission().name()
                        : FileMessage.DownloadPermission.CAN_DOWNLOAD.name())
                .pinned(pinned)
                .build();
    }

    private static FileMessage.DownloadPermission parsePermission(String raw) {
        if (raw == null || raw.isBlank()) return FileMessage.DownloadPermission.CAN_DOWNLOAD;
        try { return FileMessage.DownloadPermission.valueOf(raw.toUpperCase()); }
        catch (IllegalArgumentException e) { return FileMessage.DownloadPermission.CAN_DOWNLOAD; }
    }
}
