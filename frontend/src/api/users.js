import client from './client';

// Profile quirks: fetching the profile is a POST; username and password
// updates travel as query parameters, not JSON bodies.

export function fetchProfile() {
  return client.post('/users/me');
}

export function updateUsername(username) {
  return client.patch('/users/me', null, { params: { username } });
}

export function changePassword(password) {
  return client.put('/users/me/password', null, { params: { password } });
}

export function deleteAccount(password) {
  return client.delete('/users/me', { data: { password } });
}
