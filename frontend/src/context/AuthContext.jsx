import { createContext, useContext, useState, useEffect } from 'react';
import { users } from '../services/api';

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [currentUser, setCurrentUser] = useState(null);
  const [loading, setLoading]         = useState(true);

  useEffect(() => {
    const token = localStorage.getItem('accessToken');
    if (token) {
      users.getMe()
        .then(res => setCurrentUser(res.data))
        .catch(() => localStorage.clear())
        .finally(() => setLoading(false));
    } else {
      setLoading(false);
    }
  }, []);

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
      .then(res => setCurrentUser(prev => ({ ...prev, ...res.data })))
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
    <AuthContext.Provider value={{ currentUser, loading, login, logout, updateProfile }}>
      {children}
    </AuthContext.Provider>
  );
}

export const useAuth = () => useContext(AuthContext);
