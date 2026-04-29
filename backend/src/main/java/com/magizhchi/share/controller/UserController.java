package com.magizhchi.share.controller;

import com.magizhchi.share.dto.response.UserSearchResponse;
import com.magizhchi.share.exception.AppException;
import com.magizhchi.share.model.User;
import com.magizhchi.share.repository.UserRepository;
import com.magizhchi.share.service.ConnectionService;
import com.magizhchi.share.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository     userRepo;
    private final FileStorageService storage;
    private final ConnectionService  connService;

    // ── Profile ───────────────────────────────────────────────────────────────

    /**
     * Refresh the stored presigned profile-photo URL so the caller always
     * gets a non-expired link. This avoids the "blank avatar after a few
     * hours" bug — the DB stores a presigned URL that expires after the
     * configured duration; we regenerate it from the s3 key on every read.
     */
    private void refreshPhoto(User user) {
        if (user == null || user.getProfilePhotoUrl() == null) return;
        String fresh = storage.refreshProfilePhotoUrl(user.getProfilePhotoUrl());
        if (fresh != null) user.setProfilePhotoUrl(fresh);
    }

    @GetMapping("/me")
    public ResponseEntity<User> getMe(@AuthenticationPrincipal User user) {
        refreshPhoto(user);
        return ResponseEntity.ok(user);
    }

    @PatchMapping("/me")
    public ResponseEntity<User> updateMe(
            @AuthenticationPrincipal User user,
            @RequestBody Map<String, String> updates) {

        if (updates.containsKey("displayName") && !updates.get("displayName").isBlank()) {
            user.setDisplayName(updates.get("displayName").trim());
        }
        if (updates.containsKey("statusMessage")) {
            user.setStatusMessage(updates.get("statusMessage"));
        }
        if (updates.containsKey("email")) {
            String email = updates.get("email").trim();
            if (userRepo.existsByEmail(email) && !email.equals(user.getEmail())) {
                throw new AppException(HttpStatus.CONFLICT, "Email already in use.");
            }
            user.setEmail(email);
        }
        User saved = userRepo.save(user);
        refreshPhoto(saved);
        return ResponseEntity.ok(saved);
    }

    @PostMapping("/me/photo")
    public ResponseEntity<Map<String, String>> uploadPhoto(
            @AuthenticationPrincipal User user,
            @RequestParam("file") MultipartFile file) {

        String url = storage.uploadProfilePhoto(file, user.getId());
        user.setProfilePhotoUrl(url);
        userRepo.save(user);
        return ResponseEntity.ok(Map.of("photoUrl", url));
    }

    // ── Search / Lookup ───────────────────────────────────────────────────────

    @GetMapping("/search")
    public ResponseEntity<List<UserSearchResponse>> search(
            @RequestParam("q") String query,
            @AuthenticationPrincipal User caller) {
        if (query == null || query.trim().length() < 2) {
            return ResponseEntity.ok(List.of());
        }
        List<User> raw = userRepo.searchUsers(query.trim());
        return ResponseEntity.ok(connService.enrichSearchResults(raw, caller.getId()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<User> getById(@PathVariable Long id) {
        User u = userRepo.findById(id)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "User not found."));
        refreshPhoto(u);
        return ResponseEntity.ok(u);
    }
}
