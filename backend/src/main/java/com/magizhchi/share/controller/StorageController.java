package com.magizhchi.share.controller;

import com.magizhchi.share.dto.response.StorageResponse;
import com.magizhchi.share.model.User;
import com.magizhchi.share.service.StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/storage")
@RequiredArgsConstructor
public class StorageController {

    private final StorageService storageService;

    /**
     * GET /api/storage/usage
     * Returns full storage breakdown for the authenticated user.
     */
    @GetMapping("/usage")
    public ResponseEntity<StorageResponse> getUsage(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(storageService.getUsage(user.getId()));
    }
}
