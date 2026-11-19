import axios from 'axios';

// Central API client.
// Backend envelope: { code, msg, data }. Business errors arrive as HTTP 200
// with a non-200 code; auth failures arrive as a bare HTTP 401 with no body.

export const TOKEN_KEY = 'lifenote_token';
export const USER_KEY = 'lifenote_user';

export class ApiError extends Error {
  constructor(code, msg, data) {
    super(msg);
    this.name = 'ApiError';
    this.code = code;
    this.data = data;
  }
}

const client = axios.create({ baseURL: '/api', timeout: 60000 });

client.interceptors.request.use((config) => {
  const token = localStorage.getItem(TOKEN_KEY);
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

client.interceptors.response.use(
  (response) => {
    // Raw endpoints (ICS export) pass through untouched.
    if (response.config.responseType === 'blob') return response.data;

    const body = response.data;
    if (body && typeof body === 'object' && 'code' in body) {
      if (body.code === 200) return body.data;
      // Validation failures carry a field -> message map in data.
      throw new ApiError(body.code, body.msg, body.data);
    }
    return body;
  },
  (error) => {
    if (error.response && error.response.status === 401) {
      localStorage.removeItem(TOKEN_KEY);
      localStorage.removeItem(USER_KEY);
      if (!window.location.pathname.startsWith('/login')) {
        window.location.assign('/login');
      }
    }
    return Promise.reject(error);
  }
);

export function setSession(token, user) {
  localStorage.setItem(TOKEN_KEY, token);
  localStorage.setItem(USER_KEY, JSON.stringify(user));
}

export function clearSession() {
  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(USER_KEY);
}

export function loadStoredUser() {
  try {
    return JSON.parse(localStorage.getItem(USER_KEY)) || null;
  } catch {
    return null;
  }
}

export default client;
