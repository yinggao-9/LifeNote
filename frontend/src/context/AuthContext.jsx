import { createContext, useContext, useState } from 'react';
import { login as apiLogin, logout as apiLogout, register as apiRegister } from '../api/auth';
import { loadStoredUser, clearSession, USER_KEY } from '../api/client';

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [user, setUserState] = useState(loadStoredUser);

  // Wrapper keeps localStorage in sync so a reload shows fresh profile data.
  function setUser(next) {
    setUserState(next);
    if (next) localStorage.setItem(USER_KEY, JSON.stringify(next));
  }

  async function login(email, password) {
    const data = await apiLogin(email, password);
    setUser({ userId: data.userId, email: data.email, username: data.username });
    return data;
  }

  async function register(email, username, password) {
    await apiRegister(email, username, password);
  }

  async function logout() {
    // Best-effort: the token may already be expired on the server side.
    try {
      await apiLogout();
    } catch {
      clearSession();
    }
    setUserState(null);
  }

  function forceLogout() {
    clearSession();
    setUserState(null);
  }

  return (
    <AuthContext.Provider value={{ user, setUser, login, register, logout, forceLogout }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  return useContext(AuthContext);
}
