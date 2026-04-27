package com.magizhchi.share.controller;

import com.magizhchi.share.dto.response.ConnectionRequestResponse;
import com.magizhchi.share.dto.response.UserSearchResponse;
import com.magizhchi.share.model.User;
import com.magizhchi.share.service.ConnectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * All connection-request and block endpoints.
 *
 * POST   /api/connections/request/{userId}              – send request
 * POST   /api/connections/request/{id}/accept           – accept received request
 * POST   /api/connections/request/{id}/reject           – reject received request
 * DELETE /api/connections/request/{id}                  – cancel sent request
 * GET    /api/connections/requests/received             – pending inbox
 * GET    /api/connections/requests/sent                 – pending outbox
 *
 * POST   /api/users/{userId}/block                      – block a user
 * DELETE /api/users/{userId}/block                      – unblock a user
 * GET    /api/users/blocked                             – list blocked users
 */
@RestController
@RequiredArgsConstructor
public class ConnectionController {

    private final ConnectionService connService;

    // ── Connection Requests ───────────────────────────────────────────────────

    @PostMapping("/api/connections/request/{userId}")
    public ResponseEntity<ConnectionRequestResponse> sendRequest(
            @PathVariable Long userId,
            @AuthenticationPrincipal User caller) {
        return ResponseEntity.ok(connService.sendRequest(caller.getId(), userId));
    }

    @PostMapping("/api/connections/request/{id}/accept")
    public ResponseEntity<ConnectionRequestResponse> accept(
            @PathVariable Long id,
            @AuthenticationPrincipal User caller) {
        return ResponseEntity.ok(connService.acceptRequest(id, caller.getId()));
    }

    @PostMapping("/api/connections/request/{id}/reject")
    public ResponseEntity<ConnectionRequestResponse> reject(
            @PathVariable Long id,
            @AuthenticationPrincipal User caller) {
        return ResponseEntity.ok(connService.rejectRequest(id, caller.getId()));
    }

    @DeleteMapping("/api/connections/request/{id}")
    public ResponseEntity<Map<String, String>> cancel(
            @PathVariable Long id,
            @AuthenticationPrincipal User caller) {
        connService.cancelRequest(id, caller.getId());
        return ResponseEntity.ok(Map.of("message", "Request cancelled."));
    }

    @GetMapping("/api/connections/requests/received")
    public ResponseEntity<List<ConnectionRequestResponse>> received(
            @AuthenticationPrincipal User caller) {
        return ResponseEntity.ok(connService.getReceivedRequests(caller.getId()));
    }

    @GetMapping("/api/connections/requests/sent")
    public ResponseEntity<List<ConnectionRequestResponse>> sent(
            @AuthenticationPrincipal User caller) {
        return ResponseEntity.ok(connService.getSentRequests(caller.getId()));
    }

    // ── Unfriend ──────────────────────────────────────────────────────────────

    @DeleteMapping("/api/connections/unfriend/{userId}")
    public ResponseEntity<Map<String, String>> unfriend(
            @PathVariable Long userId,
            @AuthenticationPrincipal User caller) {
        connService.unfriend(caller.getId(), userId);
        return ResponseEntity.ok(Map.of("message", "Connection removed."));
    }

    // ── Block / Unblock ───────────────────────────────────────────────────────

    @PostMapping("/api/users/{userId}/block")
    public ResponseEntity<Map<String, String>> block(
            @PathVariable Long userId,
            @AuthenticationPrincipal User caller) {
        connService.blockUser(caller.getId(), userId);
        return ResponseEntity.ok(Map.of("message", "User blocked."));
    }

    @DeleteMapping("/api/users/{userId}/block")
    public ResponseEntity<Map<String, String>> unblock(
            @PathVariable Long userId,
            @AuthenticationPrincipal User caller) {
        connService.unblockUser(caller.getId(), userId);
        return ResponseEntity.ok(Map.of("message", "User unblocked."));
    }

    @GetMapping("/api/users/blocked")
    public ResponseEntity<List<UserSearchResponse>> blockedList(
            @AuthenticationPrincipal User caller) {
        return ResponseEntity.ok(connService.getBlockedUsers(caller.getId()));
    }
}
