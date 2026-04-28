package com.magizhchi.share.network;

import android.content.Context;
import android.content.SharedPreferences;

public class TokenManager {

    private static final String PREF_NAME = "magizhchi_prefs";
    private static final String KEY_ACCESS_TOKEN = "access_token";
    private static final String KEY_REFRESH_TOKEN = "refresh_token";
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_DISPLAY_NAME = "display_name";
    private static final String KEY_PROFILE_PHOTO_URL = "profile_photo_url";
    private static final String KEY_EMAIL = "email";

    private static TokenManager instance;
    private final SharedPreferences prefs;

    private TokenManager(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static synchronized TokenManager getInstance(Context context) {
        if (instance == null) {
            instance = new TokenManager(context);
        }
        return instance;
    }

    public void saveTokens(String accessToken, String refreshToken) {
        prefs.edit()
                .putString(KEY_ACCESS_TOKEN, accessToken)
                .putString(KEY_REFRESH_TOKEN, refreshToken)
                .apply();
    }

    public void saveUserInfo(String userId, String displayName, String email, String profilePhotoUrl) {
        prefs.edit()
                .putString(KEY_USER_ID, userId)
                .putString(KEY_DISPLAY_NAME, displayName)
                .putString(KEY_EMAIL, email)
                .putString(KEY_PROFILE_PHOTO_URL, profilePhotoUrl)
                .apply();
    }

    public String getAccessToken() {
        return prefs.getString(KEY_ACCESS_TOKEN, null);
    }

    public String getRefreshToken() {
        return prefs.getString(KEY_REFRESH_TOKEN, null);
    }

    public String getUserId() {
        return prefs.getString(KEY_USER_ID, null);
    }

    public String getDisplayName() {
        return prefs.getString(KEY_DISPLAY_NAME, null);
    }

    public String getEmail() {
        return prefs.getString(KEY_EMAIL, null);
    }

    public String getProfilePhotoUrl() {
        return prefs.getString(KEY_PROFILE_PHOTO_URL, null);
    }

    public void setAccessToken(String accessToken) {
        prefs.edit().putString(KEY_ACCESS_TOKEN, accessToken).apply();
    }

    public void clear() {
        prefs.edit().clear().apply();
    }
}
