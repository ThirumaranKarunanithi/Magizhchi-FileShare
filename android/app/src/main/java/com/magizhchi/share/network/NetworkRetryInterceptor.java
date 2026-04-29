package com.magizhchi.share.network;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Retries calls that fail with transient network errors. Mostly needed on
 * mobile, where the user may briefly lose connectivity (Wi-Fi → mobile
 * handoff, screen-off doze, signal dip, captive portal) right at the moment
 * a request fires. Without retries, a one-shot {@link UnknownHostException}
 * surfaces all the way to the UI as the raw "Unable to resolve host" text
 * — even though the next call moments later would have succeeded.
 *
 * <p>Retry policy:
 * <ul>
 *   <li>Up to {@value #MAX_ATTEMPTS} attempts (1 initial + 2 retries)</li>
 *   <li>Backoff: 250 ms then 750 ms — short enough that the user doesn't
 *       perceive the lag, long enough to let DNS / radio recover</li>
 *   <li>Only retries on {@link UnknownHostException} / {@link ConnectException}
 *       / {@link SocketTimeoutException}. Doesn't retry on a 5xx — that's
 *       the server's problem, the caller decides what to do.</li>
 *   <li>Doesn't retry POST / PUT / PATCH / DELETE bodies that may have
 *       partial side effects on the server. Only idempotent verbs (GET / HEAD)
 *       are retried; everything else fails fast with the original exception
 *       so we don't accidentally double-create a connection request.</li>
 * </ul>
 */
public class NetworkRetryInterceptor implements Interceptor {

    private static final int MAX_ATTEMPTS = 3;
    private static final long[] BACKOFF_MS = { 0L, 250L, 750L };

    @Override
    public Response intercept(Chain chain) throws IOException {
        Request request = chain.request();
        boolean idempotent = isIdempotent(request.method());

        IOException lastFailure = null;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            if (BACKOFF_MS[attempt] > 0) {
                try { Thread.sleep(BACKOFF_MS[attempt]); }
                catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted while retrying", lastFailure);
                }
            }

            try {
                return chain.proceed(request);
            } catch (UnknownHostException | ConnectException | SocketTimeoutException e) {
                lastFailure = e;
                // Bail immediately on non-idempotent verbs — retrying a POST
                // could double-send a side effect (e.g. a duplicate file
                // upload or a duplicate connection request).
                if (!idempotent) throw e;
                // else fall through to the next attempt
            }
        }
        // All attempts exhausted — surface the last exception unchanged so
        // existing onFailure handlers can map it to a user-friendly message.
        throw lastFailure;
    }

    private static boolean isIdempotent(String method) {
        return "GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method);
    }
}
