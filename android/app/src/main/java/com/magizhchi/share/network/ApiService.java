package com.magizhchi.share.network;

import com.magizhchi.share.model.AuthResponse;
import com.magizhchi.share.model.ConnectionRequestResponse;
import com.magizhchi.share.model.ConversationResponse;
import com.magizhchi.share.model.FileMessageResponse;
import com.magizhchi.share.model.FolderResponse;
import com.magizhchi.share.model.GroupMemberResponse;
import com.magizhchi.share.model.StorageUsageResponse;
import com.magizhchi.share.model.UserSearchResponse;

import java.util.List;
import java.util.Map;

import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.Multipart;
import retrofit2.http.PATCH;
import retrofit2.http.POST;
import retrofit2.http.Part;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface ApiService {

    // ── Auth ──────────────────────────────────────────────────────────────────

    /** Login: send OTP to existing account (identifier = email or mobile) */
    @POST("api/auth/login/send-otp")
    Call<ResponseBody> sendLoginOtp(@Body Map<String, String> body);

    /** Login: verify OTP → returns JWT */
    @POST("api/auth/login/verify")
    Call<AuthResponse> verifyLoginOtp(@Body Map<String, String> body);

    /**
     * Register: send OTP for new account.
     * Body must include: displayName, mobileNumber, email, otpChannel ("EMAIL"|"SMS")
     */
    @POST("api/auth/register/send-otp")
    Call<ResponseBody> sendRegisterOtp(@Body Map<String, String> body);

    /** Register: verify OTP → creates account + returns JWT */
    @POST("api/auth/register/verify")
    Call<AuthResponse> verifyRegisterOtp(@Body Map<String, String> body);

    @POST("api/auth/refresh")
    Call<AuthResponse> refreshToken(@Body Map<String, String> body);

    // ── Conversations ─────────────────────────────────────────────────────────

    @GET("api/conversations")
    Call<List<ConversationResponse>> getConversations();

    /** The caller's personal-storage conversation (auto-created on first call). */
    @GET("api/conversations/personal")
    Call<ConversationResponse> getPersonalConversation();

    /** Files shared WITH the current user — used for the Shared Files card stats. */
    @GET("api/share/shared-with-me")
    Call<List<FileMessageResponse>> getSharedWithMe();

    /**
     * Returns a Page<FileMessageResponse>. Use ResponseBody + manual Gson parsing
     * to extract the "content" list, supporting pagination.
     */
    @GET("api/conversations/{id}/files")
    Call<ResponseBody> getConversationFiles(
            @Path("id") String conversationId,
            @Query("page") int page,
            @Query("size") int size
    );

    @GET("api/conversations/{id}/members")
    Call<List<GroupMemberResponse>> getConversationMembers(@Path("id") String conversationId);

    @POST("api/conversations/{id}/members/{userId}")
    Call<ResponseBody> addMember(@Path("id") String conversationId, @Path("userId") String userId);

    @DELETE("api/conversations/{id}/members/{userId}")
    Call<ResponseBody> removeMember(@Path("id") String conversationId, @Path("userId") String userId);

    @PATCH("api/conversations/{id}/members/{userId}/role")
    Call<ResponseBody> updateMemberRole(
            @Path("id") String conversationId,
            @Path("userId") String userId,
            @Query("role") String role
    );

    /**
     * Backend signature is a multipart with two NAMED parts:
     *   data — JSON of CreateGroupRequest { name, memberIds[] }
     *   icon — optional binary MultipartFile
     *
     * The previous overload sent a flat "name" part which the @RequestPart
     * binder couldn't deserialize into CreateGroupRequest, hence the
     * persistent "failed to create group" error. Use createGroupWithData(...)
     * going forward.
     */
    @Multipart
    @POST("api/conversations/group")
    Call<ConversationResponse> createGroup(
            @Part("name") RequestBody name,
            @Part MultipartBody.Part icon
    );

    @Multipart
    @POST("api/conversations/group")
    Call<ConversationResponse> createGroupWithData(
            @Part("data") RequestBody data,
            @Part MultipartBody.Part icon
    );

    // ── Files ─────────────────────────────────────────────────────────────────

    @GET("api/files/{id}/download-url")
    Call<Map<String, String>> getDownloadUrl(@Path("id") String fileId);

    /**
     * Inline-disposition presigned URL — for in-browser preview. Always
     * accessible to any conversation member (VIEW_ONLY files can still be
     * previewed; only the download endpoint enforces download permission).
     */
    @GET("api/files/{id}/preview-url")
    Call<Map<String, String>> getPreviewUrl(@Path("id") String fileId);

    @DELETE("api/files/{id}")
    Call<ResponseBody> deleteFile(@Path("id") String fileId);

    @GET("api/files/search")
    Call<List<FileMessageResponse>> searchFiles(@Query("q") String query);

    @Multipart
    @POST("api/files/send/{conversationId}")
    Call<FileMessageResponse> sendFile(
            @Path("conversationId") String conversationId,
            @Part MultipartBody.Part file,
            @Part("caption") RequestBody caption,
            @Part("mentionedUserIds") RequestBody mentionedUserIds
    );

    /**
     * Same upload endpoint, but with the optional permission + folderPath
     * fields the backend supports. Used by UploadActivity so the user can
     * pick a download permission (Anyone / View only / Admins) and target
     * a folder explicitly.
     */
    @Multipart
    @POST("api/files/send/{conversationId}")
    Call<FileMessageResponse> sendFileWithOptions(
            @Path("conversationId") String conversationId,
            @Part MultipartBody.Part file,
            @Part("caption") RequestBody caption,
            @Part("mentionedUserIds") RequestBody mentionedUserIds,
            @Part("permission") RequestBody permission,
            @Part("folderPath") RequestBody folderPath
    );

    // ── Storage ───────────────────────────────────────────────────────────────

    @GET("api/storage/usage")
    Call<StorageUsageResponse> getStorageUsage();

    // ── Users ─────────────────────────────────────────────────────────────────

    @GET("api/users/search")
    Call<List<UserSearchResponse>> searchUsers(@Query("q") String query);

    @GET("api/users/me")
    Call<AuthResponse> getMe();

    /** Fetch a user's public profile (display name, photo, statusMessage, …). */
    @GET("api/users/{userId}")
    Call<UserSearchResponse> getUserById(@retrofit2.http.Path("userId") String userId);

    /**
     * Update the caller's profile fields. Body keys (all optional):
     *   displayName    — non-blank to apply
     *   statusMessage  — empty string clears it
     *   email          — must not collide with another user
     */
    @PATCH("api/users/me")
    Call<ResponseBody> updateMe(@Body Map<String, String> updates);

    /**
     * Upload a new profile photo. Returns { photoUrl: <fresh presigned URL> }.
     */
    @Multipart
    @POST("api/users/me/photo")
    Call<Map<String, String>> uploadProfilePhoto(@Part MultipartBody.Part file);

    // ── Folders ───────────────────────────────────────────────────────────────

    /**
     * Create a new folder. Body keys:
     *   name              (required) — folder name
     *   conversationId    (required) — host conversation id
     *   parentFolderId    (optional) — null = root level
     *   defaultPermission (optional) — "CAN_DOWNLOAD" / "VIEW_ONLY" / "ADMIN_ONLY_DOWNLOAD"
     *   description       (optional)
     */
    @POST("api/folders")
    Call<FolderResponse> createFolder(@Body Map<String, Object> body);

    // ── Connections ───────────────────────────────────────────────────────────

    @DELETE("api/connections/unfriend/{userId}")
    Call<ResponseBody> unfriend(@Path("userId") String userId);

    @POST("api/users/{userId}/block")
    Call<ResponseBody> blockUser(@Path("userId") String userId);

    /**
     * Open (or create) the direct-chat conversation between the caller and
     * the target user. Returns the conversation; clients launch ChatActivity
     * with this. Mirrors the web's {@code openDirect}.
     */
    @POST("api/conversations/direct/{targetUserId}")
    Call<ConversationResponse> openDirectConversation(@Path("targetUserId") String targetUserId);

    /** Send a connection request to another user. */
    @POST("api/connections/request/{userId}")
    Call<ConnectionRequestResponse> sendConnectionRequest(@Path("userId") String userId);

    // ── Notifications (connection requests inbox) ─────────────────────────────

    /** Pending requests other users have sent to me. Drives NotificationsActivity. */
    @GET("api/connections/requests/received")
    Call<List<ConnectionRequestResponse>> getReceivedRequests();

    /** Accept a pending connection request. */
    @POST("api/connections/request/{id}/accept")
    Call<ConnectionRequestResponse> acceptRequest(@Path("id") String requestId);

    /** Reject a pending connection request. */
    @POST("api/connections/request/{id}/reject")
    Call<ConnectionRequestResponse> rejectRequest(@Path("id") String requestId);
}
