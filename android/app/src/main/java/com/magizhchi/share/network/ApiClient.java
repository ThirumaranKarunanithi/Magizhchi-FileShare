package com.magizhchi.share.network;

import android.content.Context;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.magizhchi.share.model.AuthResponse;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class ApiClient {

    // TODO: Replace with your actual Railway backend URL before running
    // Example: "https://magizhchi-share-production.up.railway.app/"
    public static final String BASE_URL = "https://box.magizhchi.software/";

    private static ApiClient instance;
    private final ApiService apiService;
    private final Context appContext;

    private ApiClient(Context context) {
        this.appContext = context.getApplicationContext();

        HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
        logging.setLevel(HttpLoggingInterceptor.Level.BODY);

        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .addInterceptor(new AuthInterceptor())
                .addInterceptor(new TokenRefreshInterceptor())
                .addInterceptor(logging)
                .build();

        Gson gson = new GsonBuilder()
                .setLenient()
                .create();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create(gson))
                .build();

        apiService = retrofit.create(ApiService.class);
    }

    public static synchronized ApiClient getInstance(Context context) {
        if (instance == null) {
            instance = new ApiClient(context);
        }
        return instance;
    }

    public ApiService getApiService() {
        return apiService;
    }

    // ── Auth interceptor: adds Bearer token to every request ──────────────────

    private class AuthInterceptor implements Interceptor {
        @Override
        public Response intercept(Chain chain) throws IOException {
            TokenManager tokenManager = TokenManager.getInstance(appContext);
            String token = tokenManager.getAccessToken();

            Request original = chain.request();
            if (token != null && !token.isEmpty()) {
                Request authenticated = original.newBuilder()
                        .header("Authorization", "Bearer " + token)
                        .build();
                return chain.proceed(authenticated);
            }
            return chain.proceed(original);
        }
    }

    // ── Token refresh interceptor: on 401, refresh and retry once ─────────────

    private class TokenRefreshInterceptor implements Interceptor {
        @Override
        public Response intercept(Chain chain) throws IOException {
            Response response = chain.proceed(chain.request());

            if (response.code() == 401) {
                response.close();
                TokenManager tokenManager = TokenManager.getInstance(appContext);
                String refreshToken = tokenManager.getRefreshToken();

                if (refreshToken == null) {
                    return response;
                }

                // Build a plain OkHttpClient without interceptors to avoid infinite loop
                OkHttpClient plainClient = new OkHttpClient.Builder()
                        .connectTimeout(30, TimeUnit.SECONDS)
                        .readTimeout(30, TimeUnit.SECONDS)
                        .build();

                Gson gson = new Gson();
                Retrofit tempRetrofit = new Retrofit.Builder()
                        .baseUrl(BASE_URL)
                        .client(plainClient)
                        .addConverterFactory(GsonConverterFactory.create(gson))
                        .build();

                ApiService tempService = tempRetrofit.create(ApiService.class);
                Map<String, String> body = new HashMap<>();
                body.put("refreshToken", refreshToken);

                try {
                    retrofit2.Response<AuthResponse> refreshResponse =
                            tempService.refreshToken(body).execute();

                    if (refreshResponse.isSuccessful() && refreshResponse.body() != null) {
                        String newAccessToken = refreshResponse.body().getAccessToken();
                        String newRefreshToken = refreshResponse.body().getRefreshToken();
                        tokenManager.saveTokens(newAccessToken, newRefreshToken);

                        // Retry original request with new token
                        Request retryRequest = chain.request().newBuilder()
                                .header("Authorization", "Bearer " + newAccessToken)
                                .build();
                        return chain.proceed(retryRequest);
                    }
                } catch (Exception e) {
                    // Refresh failed — clear tokens so MainActivity redirects to Login
                    tokenManager.clear();
                }
            }

            return response;
        }
    }
}
