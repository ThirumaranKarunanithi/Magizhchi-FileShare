import { createContext, useContext, useState, useEffect, useRef, useCallback } from 'react';
import { users } from '../services/api';

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [currentUser, setCurrentUser] = useState(null);
  const [loading, setLoading]         = useState(true);

  // Track the last time we re-fetched /api/users/me so we can throttle
  // refresh-on-error / refresh-on-focus and avoid hammering the server.
  const lastRefreshRef = useRef(0);

  /**
   * Re-fetch /api/users/me. The backend re-presigns the profile photo URL
   * on every read — calling this swaps in a fresh, non-expired URL into
   * currentUser, which fixes the "blank avatar after a few hours" bug
   * (presigned S3 URLs have a short TTL).
   *
   * Throttled to once every 30 s so an `onError` retry loop on a broken
   * image doesn't spam the endpoint.
   */
  const refreshMe = useCallback(async () => {
    const now = Date.now();
    if (now - lastRefreshRef.current < 30_000) return;
    if (!localStorage.getItem('accessToken')) return;
    lastRefreshRef.current = now;
    try {
      const res = await users.getMe();
      setCurrentUser(prev => ({ ...(prev || {}), ...res.data }));
    } catch { /* network blip — try again later */ }
  }, []);

  useEffect(() => {
    const token = localStorage.getItem('accessToken');
    if (token) {
      users.getMe()
        .then(res => { setCurrentUser(res.data); lastRefreshRef.current = Date.now(); })
        .catch(() => localStorage.clear())
        .finally(() => setLoading(false));
    } else {
      setLoading(false);
    }
  }, []);

  // Refresh on tab visibility change — when the user comes back to the tab
  // after the URL TTL has elapsed, kick a getMe so the avatar URL is fresh.
  useEffect(() => {
    const handler = () => { if (document.visibilityState === 'visible') refreshMe(); };
    document.addEventListener('visibilitychange', handler);
    return () => document.removeEventListener('visibilitychange', handler);
  }, [refreshMe]);

  const login = (authData) => {
    localStorage.setItem('accessToken',  authData.accessToken);
    localStorage.setItem('refreshToken', authData.refreshToken);
    // Seed currentUser with whatever the auth response gave us so the UI can
    // render immediately. The auth payload doesn't include every field
    // (notably statusMessage + lastSeenAt) — fire users.getMe() in the
    // background to fill those in. This is what makes the profile pic AND
    // status remain visible after a session-lost → re-login cycle, instead of
    // disappearing until the next hard refresh.
    setCurrentUser({
      id:              authData.userId,
      displayName:     authData.displayName,
      mobileNumber:    authData.mobileNumber,
      email:           authData.email,
      profilePhotoUrl: authData.profilePhotoUrl,
    });
    users.getMe()
      .then(res => { setCurrentUser(prev => ({ ...prev, ...res.data })); lastRefreshRef.current = Date.now(); })
      .catch(() => {/* keep the partial seed; getMe will retry on next mount */});
  };

  const logout = () => {
    localStorage.clear();
    setCurrentUser(null);
  };

  const updateProfile = (updates) => {
    setCurrentUser(prev => ({ ...prev, ...updates }));
  };

  return (
    <AuthContext.Provider value={{ currentUser, loading, login, logout, updateProfile, refreshMe }}>
      {children}
    </AuthContext.Provider>
  );
}

export const useAuth = () => useContext(AuthContext);
