package com.magizhchi.share.service;

import com.magizhchi.share.config.FileProperties;
import com.magizhchi.share.exception.AppException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.io.IOException;
import java.time.Duration;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class FileStorageService {

    private final S3Client        s3Client;
    private final S3Presigner     s3Presigner;
    private final FileProperties  fileProperties;

    @Value("${aws.s3.bucket-name}")
    private String bucket;

    @Value("${aws.s3.presigned-url-expiry-minutes}")
    private int presignedUrlExpiryMinutes;

    // ── Upload ────────────────────────────────────────────────────────────────

    /**
     * Validate, upload file to S3, return S3 key.
     * Path: files/{conversationId}/{uuid}.{ext}
     */
    public String uploadFile(MultipartFile file, Long conversationId) {
        validateFile(file);

        String ext = getExtension(file.getOriginalFilename());
        String key = "files/" + conversationId + "/" + UUID.randomUUID() + ext;

        try {
            PutObjectRequest req = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .contentType(file.getContentType())
                    .contentDisposition("attachment; filename=\"" + sanitize(file.getOriginalFilename()) + "\"")
                    .build();

            s3Client.putObject(req, RequestBody.fromBytes(file.getBytes()));
            log.info("Uploaded file: key={}, size={}", key, file.getSize());
            return key;

        } catch (Exception e) {
            log.error("File upload failed: key={}, error={}", key, e.getMessage(), e);
            throw new AppException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "File upload failed: " + e.getMessage());
        }
    }

    /**
     * Upload a profile photo. Path: profiles/{userId}/{uuid}.{ext}
     */
    public String uploadProfilePhoto(MultipartFile file, Long userId) {
        if (file == null || file.isEmpty()) {
            throw new AppException(HttpStatus.BAD_REQUEST, "No file provided.");
        }
        String ct = file.getContentType();
        if (ct == null || !ct.startsWith("image/")) {
            throw new AppException(HttpStatus.BAD_REQUEST,
                    "Only image files are allowed for profile photos (received: " + ct + ").");
        }
        String ext = getExtension(file.getOriginalFilename());
        String key = "profiles/" + userId + "/" + UUID.randomUUID() + ext;
        try {
            PutObjectRequest req = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .contentType(ct)
                    .build();
            s3Client.putObject(req, RequestBody.fromBytes(file.getBytes()));
            log.info("Uploaded profile photo: userId={}, key={}", userId, key);
            return generatePresignedUrl(key);
        } catch (Exception e) {
            log.error("Profile photo upload failed: userId={}, error={}", userId, e.getMessage(), e);
            throw new AppException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Photo upload failed: " + e.getMessage());
        }
    }

    // ── Download ──────────────────────────────────────────────────────────────

    /** Generate a short-lived presigned GET URL for a file (forces download). */
    public String generatePresignedUrl(String s3Key) {
        GetObjectPresignRequest presignReq = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(presignedUrlExpiryMinutes))
                .getObjectRequest(GetObjectRequest.builder()
                        .bucket(bucket)
                        .key(s3Key)
                        .build())
                .build();
        return s3Presigner.presignGetObject(presignReq).url().toString();
    }

    /**
     * Generate a presigned URL that opens inline in the browser (for preview).
     * Sets response-content-disposition to "inline" so browsers render it directly.
     */
    public String generateInlinePresignedUrl(String s3Key) {
        GetObjectPresignRequest presignReq = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(presignedUrlExpiryMinutes))
                .getObjectRequest(GetObjectRequest.builder()
                        .bucket(bucket)
                        .key(s3Key)
                        .responseContentDisposition("inline")
                        .build())
                .build();
        return s3Presigner.presignGetObject(presignReq).url().toString();
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    public void deleteFile(String s3Key) {
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucket)
                    .key(s3Key)
                    .build());
            log.info("Deleted file: key={}", s3Key);
        } catch (Exception e) {
            log.warn("Could not delete S3 key {}: {}", s3Key, e.getMessage());
        }
    }

    // ── Validation ────────────────────────────────────────────────────────────

    private void validateFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new AppException(HttpStatus.BAD_REQUEST, "File is empty.");
        }
        long maxBytes = fileProperties.getMaxSizeBytes();
        if (file.getSize() > maxBytes) {
            throw new AppException(HttpStatus.PAYLOAD_TOO_LARGE,
                    "File exceeds maximum size of " + (maxBytes / 1024 / 1024) + " MB.");
        }
        String ct = file.getContentType();
        var blocked = fileProperties.getBlockedTypes();
        if (ct != null && !blocked.isEmpty() && blocked.contains(ct)) {
            throw new AppException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "This file type is not allowed: " + ct);
        }
    }

    private String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) return "";
        return "." + filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
    }

    private String sanitize(String filename) {
        if (filename == null) return "file";
        return filename.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
