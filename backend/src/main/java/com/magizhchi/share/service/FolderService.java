package com.magizhchi.share.service;

import com.magizhchi.share.dto.request.CreateFolderRequest;
import com.magizhchi.share.dto.response.FolderResponse;
import com.magizhchi.share.exception.AppException;
import com.magizhchi.share.model.Folder;
import com.magizhchi.share.repository.ConversationMemberRepository;
import com.magizhchi.share.repository.ConversationRepository;
import com.magizhchi.share.repository.FolderRepository;
import com.magizhchi.share.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class FolderService {

    private final FolderRepository              folderRepo;
    private final ConversationRepository        convRepo;
    private final ConversationMemberRepository  memberRepo;
    private final UserRepository                userRepo;
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

        Folder folder = Folder.builder()
                .name(req.getName())
                .conversation(conv)
                .createdBy(creator)
                .parent(parent)
                .build();
        folderRepo.save(folder);

        activityService.record("FOLDER_CREATED", userId, req.getConversationId(),
                null, folder.getId(),
                creator.getDisplayName() + " created folder "" + req.getName() + """);

        log.info("Folder created: id={}, name={}, conv={}", folder.getId(), folder.getName(), req.getConversationId());
        return toResponse(folder);
    }

    public List<FolderResponse> listFolders(Long conversationId, Long parentFolderId, Long userId) {
        requireMember(conversationId, userId);
        List<Folder> folders = parentFolderId == null
                ? folderRepo.findRootFolders(conversationId)
                : folderRepo.findChildFolders(conversationId, parentFolderId);
        return folders.stream().map(this::toResponse).toList();
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
        return path.stream().map(this::toResponse).toList();
    }

    @Transactional
    public FolderResponse renameFolder(Long folderId, String newName, Long userId) {
        Folder folder = getOwnedFolder(folderId, userId);
        folder.setName(newName);
        folderRepo.save(folder);
        return toResponse(folder);
    }

    @Transactional
    public void deleteFolder(Long folderId, Long userId) {
        Folder folder = getOwnedFolder(folderId, userId);
        softDeleteRecursive(folder);
        activityService.record("FOLDER_DELETED", userId, folder.getConversation().getId(),
                null, folderId,
                userRepo.findById(userId).map(u -> u.getDisplayName()).orElse("Someone")
                        + " deleted folder "" + folder.getName() + """);
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

    public FolderResponse toResponse(Folder f) {
        return FolderResponse.builder()
                .id(f.getId())
                .name(f.getName())
                .parentId(f.getParent() != null ? f.getParent().getId() : null)
                .conversationId(f.getConversation().getId())
                .createdById(f.getCreatedBy().getId())
                .createdByName(f.getCreatedBy().getDisplayName())
                .createdAt(f.getCreatedAt())
                .build();
    }
}
