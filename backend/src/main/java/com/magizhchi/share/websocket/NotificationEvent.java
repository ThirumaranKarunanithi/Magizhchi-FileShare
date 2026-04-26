package com.magizhchi.share.websocket;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Generic WebSocket notification envelope.
 * Used for connection-request events, block notifications, etc.
 * payload is typed as Object so any DTO can be wrapped.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class NotificationEvent {
    /** e.g. CONNECTION_REQUEST | CONNECTION_ACCEPTED */
    private String type;
    private Object payload;
}
