import { Client } from '@stomp/stompjs';

// In dev the Vite proxy forwards /ws → ws://localhost:8080/ws (see vite.config.js).
// In prod:
//   • If VITE_WS_URL is set explicitly → use it directly.
//   • Otherwise, derive from VITE_API_URL: swap https→wss / http→ws and append /ws.
//     This means setting VITE_API_URL=https://box.magizhchi.software is sufficient;
//     the WS URL becomes wss://box.magizhchi.software/ws automatically.
//   • Final fallback: same host as the page (works when backend and frontend share an origin).
const _apiBase = import.meta.env.VITE_API_URL || '';
const WS_URL   = import.meta.env.VITE_WS_URL
  || (_apiBase
        ? _apiBase.replace(/^https:/, 'wss:').replace(/^http:/, 'ws:').replace(/\/$/, '') + '/ws'
        : `${window.location.protocol === 'https:' ? 'wss' : 'ws'}://${window.location.host}/ws`);

let stompClient = null;

/**
 * All active subscriptions.
 * topic → { sub: StompSubscription | null, callback: Function }
 *
 * sub is null while we are disconnected but the caller still "holds" the subscription.
 * On reconnect we replace sub with a live one without changing the Map entry.
 */
const activeTopics = new Map();

// ── Internal subscribe helper (must be called when connected) ─────────────────

function _doSubscribe(topic, callback) {
  // Replace any stale sub for this topic
  activeTopics.get(topic)?.sub?.unsubscribe();

  const sub = stompClient.subscribe(topic, message => {
    try { callback(JSON.parse(message.body)); }
    catch (e) { console.warn('[WS] Parse error on', topic, e); }
  });

  activeTopics.set(topic, { sub, callback });
}

// ── Public API ────────────────────────────────────────────────────────────────

export function connectSocket(onConnected, onDisconnected) {
  if (stompClient?.active) return; // already connecting or connected

  stompClient = new Client({
    brokerURL: WS_URL,
    connectHeaders: {
      Authorization: `Bearer ${localStorage.getItem('accessToken') || ''}`,
    },
    reconnectDelay: 4000,

    onConnect: () => {
      console.log('[WS] Connected');
      // Re-apply every tracked subscription (handles first connect AND reconnects)
      activeTopics.forEach(({ callback }, topic) => _doSubscribe(topic, callback));
      onConnected?.();
    },

    onDisconnect: () => {
      console.log('[WS] Disconnected');
      // Null out dead sub references so we don't try to unsubscribe them
      activeTopics.forEach((entry, topic) => {
        activeTopics.set(topic, { sub: null, callback: entry.callback });
      });
      onDisconnected?.();
    },

    onStompError: frame => console.error('[WS] STOMP error', frame),
  });

  stompClient.activate();
}

export function disconnectSocket() {
  stompClient?.deactivate();
  stompClient = null;
  activeTopics.clear();
}

/**
 * Subscribe to a topic.
 *
 * Safe to call BEFORE connection is established — the subscription is queued and
 * applied as soon as the socket connects (or reconnects).
 *
 * Returns an unsubscribe function.
 */
function subscribe(topic, callback) {
  // Register in the map regardless of connection state.
  // If connected right now, subscribe immediately; otherwise onConnect will pick it up.
  activeTopics.set(topic, { sub: null, callback });

  if (stompClient?.connected) {
    _doSubscribe(topic, callback);
  }

  return () => {
    const entry = activeTopics.get(topic);
    entry?.sub?.unsubscribe();
    activeTopics.delete(topic);
  };
}

/** Subscribe to a conversation's file stream (NEW_FILE, FILE_DELETED). */
export function subscribeToConversation(conversationId, callback) {
  return subscribe(`/topic/conversation/${conversationId}`, callback);
}

/**
 * Subscribe to this user's personal notification channel.
 * Receives: CONNECTION_REQUEST · CONNECTION_ACCEPTED · FILE_SHARED · NEW_FILE
 */
export function subscribeToUserNotifications(userId, callback) {
  return subscribe(`/topic/user/${userId}/notifications`, callback);
}

export function isConnected() {
  return stompClient?.connected ?? false;
}
