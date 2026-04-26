import { Client } from '@stomp/stompjs';

// In dev the Vite proxy forwards /ws → ws://localhost:8080/ws
// In prod set VITE_WS_URL e.g. wss://api.example.com/ws
const WS_URL = import.meta.env.VITE_WS_URL
  || `ws://${window.location.hostname}:8080/ws`;

let stompClient = null;
const subscriptions = new Map();

export function connectSocket(onConnected, onDisconnected) {
  if (stompClient?.connected) return;

  stompClient = new Client({
    brokerURL: WS_URL,
    connectHeaders: {
      Authorization: `Bearer ${localStorage.getItem('accessToken') || ''}`,
    },
    reconnectDelay: 5000,
    onConnect: () => {
      console.log('[WS] Connected');
      onConnected?.();
    },
    onDisconnect: () => {
      console.log('[WS] Disconnected');
      onDisconnected?.();
    },
    onStompError: frame => {
      console.error('[WS] STOMP error', frame);
    },
  });

  stompClient.activate();
}

export function disconnectSocket() {
  stompClient?.deactivate();
  stompClient = null;
  subscriptions.clear();
}

/**
 * Subscribe to a conversation topic.
 * Returns an unsubscribe function.
 */
export function subscribeToConversation(conversationId, callback) {
  if (!stompClient?.connected) return () => {};

  const topic = `/topic/conversation/${conversationId}`;

  // Avoid duplicate subscriptions
  if (subscriptions.has(topic)) {
    subscriptions.get(topic).unsubscribe();
  }

  const sub = stompClient.subscribe(topic, message => {
    try {
      callback(JSON.parse(message.body));
    } catch (e) {
      console.warn('[WS] Failed to parse message', e);
    }
  });

  subscriptions.set(topic, sub);
  return () => {
    sub.unsubscribe();
    subscriptions.delete(topic);
  };
}

/**
 * Subscribe to this user's personal notification channel.
 * Receives CONNECTION_REQUEST, CONNECTION_ACCEPTED events.
 * Returns an unsubscribe function.
 */
export function subscribeToUserNotifications(userId, callback) {
  if (!stompClient?.connected) return () => {};

  const topic = `/topic/user/${userId}/notifications`;

  if (subscriptions.has(topic)) {
    subscriptions.get(topic).unsubscribe();
  }

  const sub = stompClient.subscribe(topic, message => {
    try { callback(JSON.parse(message.body)); }
    catch (e) { console.warn('[WS] Notification parse error', e); }
  });

  subscriptions.set(topic, sub);
  return () => {
    sub.unsubscribe();
    subscriptions.delete(topic);
  };
}

export function isConnected() {
  return stompClient?.connected ?? false;
}
