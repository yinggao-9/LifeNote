import client, { setSession, clearSession } from './client';

export async function login(email, password) {
  const data = await client.post('/auth/login', { email, password });
  setSession(data.token, {
    userId: data.userId,
    email: data.email,
    username: data.username,
  });
  return data;
}

export async function register(email, username, password) {
  await client.post('/auth/register', { email, username, password });
}

export async function logout() {
  try {
    await client.post('/auth/logout');
  } finally {
    clearSession();
  }
}
