import { useCallback, useEffect, useState } from 'react';

/**
 * Browser desktop notifications (mail / WhatsApp style).
 *
 * Returns:
 *   permission   — current Notification.permission ('default' | 'granted' | 'denied' | 'unsupported')
 *   request()    — ask the user; resolves to the new permission string
 *   notify(opts) — show a system notification when permission === 'granted'
 *                  opts: { title, body, icon, tag, onClick, requireInteraction, silent }
 *
 * Requirements:
 *   - For pushes when the tab is in another window: permission must be 'granted'
 *     and at least one tab of the app must be running. (True OS-level pushes
 *     when the app is fully closed need a service-worker + Web-Push backend
 *     integration; this hook intentionally covers the common in-browser
 *     background case used by Gmail / WhatsApp Web today.)
 */
export function useDesktopNotifications() {
  const supported = typeof window !== 'undefined' && 'Notification' in window;
  const [permission, setPermission] = useState(
    supported ? Notification.permission : 'unsupported'
  );

  // Some browsers fire a `permissionchange` event on the Permissions API.
  useEffect(() => {
    if (!supported || !navigator.permissions?.query) return;
    let status;
    navigator.permissions.query({ name: 'notifications' })
      .then(s => {
        status = s;
        const update = () => setPermission(Notification.permission);
        s.addEventListener?.('change', update);
      })
      .catch(() => {});
    return () => {
      try { status?.removeEventListener?.('change', () => {}); } catch {}
    };
  }, [supported]);

  /**
   * Ask the user — MUST be called directly from a user gesture (click).
   * Handles both the modern Promise-based API and the legacy callback API
   * (some Safari versions still only support the latter).
   */
  const request = useCallback(() => {
    if (!supported) return Promise.resolve('unsupported');
    return new Promise(resolve => {
      let settled = false;
      const finish = (r) => {
        if (settled) return;
        settled = true;
        const final = r ?? Notification.permission;
        setPermission(final);
        resolve(final);
      };
      try {
        // Modern: returns a Promise
        const maybe = Notification.requestPermission(finish);
        if (maybe && typeof maybe.then === 'function') {
          maybe.then(finish, () => finish(Notification.permission));
        }
      } catch (e) {
        console.warn('[notify] requestPermission threw', e);
        finish(Notification.permission);
      }
    });
  }, [supported]);

  /**
   * Show a notification. Silently no-ops when permission isn't granted.
   * The `tag` parameter lets a follow-up notification REPLACE an earlier one
   * with the same tag (so a chat doesn't spam the OS tray).
   */
  const notify = useCallback((opts = {}) => {
    if (!supported || Notification.permission !== 'granted') return null;
    const {
      title = 'Magizhchi Box',
      body  = '',
      icon  = '/favicon.ico',
      tag,
      onClick,
      requireInteraction = false,
      silent = false,
    } = opts;
    try {
      const n = new Notification(title, { body, icon, tag, requireInteraction, silent });
      if (onClick) {
        n.onclick = (e) => {
          e.preventDefault();
          // Bring the app's window to the front before invoking the handler
          try { window.focus(); } catch {}
          try { onClick(); } catch {}
          n.close();
        };
      }
      return n;
    } catch (e) {
      // Notification can throw on some platforms when called outside a
      // service-worker context (e.g. some mobile browsers). Fail silently —
      // the in-app toast is still shown.
      console.warn('[notify] failed', e);
      return null;
    }
  }, [supported]);

  return { supported, permission, request, notify };
}
