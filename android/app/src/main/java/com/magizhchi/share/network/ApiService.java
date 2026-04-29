package com.magizhchi.share.network;

import com.magizhchi.share.model.AuthResponse;
import com.magizhchi.share.model.ConversationResponse;
import com.magizhchi.share.model.FileMessageResponse;
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

    @Multipart
    @POST("api/conversations/group")
    Call<ConversationResponse> createGroup(
            @Part("name") RequestBody name,
            @Part MultipartBody.Part icon
    );

    // ── Files ─────────────────────────────────────────────────────────────────

    @GET("api/files/{id}/download-url")
    Call<Map<String, String>> getDownloadUrl(@Path("id") String fileId);

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

    // ── Storage ───────────────────────────────────────────────────────────────

    @GET("api/storage/usage")
    Call<StorageUsageResponse> getStorageUsage();

    // ── Users ─────────────────────────────────────────────────────────────────

    @GET("api/users/search")
    Call<List<UserSearchResponse>> searchUsers(@Query("q") String query);

    @GET("api/users/me")
    Call<AuthResponse> getMe();

    // ── Connections ───────────────────────────────────────────────────────────

    @DELETE("api/connections/unfriend/{userId}")
    Call<ResponseBody> unfriend(@Path("userId") String userId);

    @POST("api/users/{userId}/block")
    Call<ResponseBody> blockUser(@Path("userId") String userId);
}
