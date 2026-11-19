import client from './client';

// Settings contract: eveningReminderTime must be sent as 'HH:mm:ss'.
// The stored default aiModel is 'default', but updates only accept
// 'gpt' or 'deepseek' — never round-trip the raw stored value.

export function getSettings() {
  return client.get('/users/me/settings');
}

export function updateSettings(payload) {
  return client.put('/users/me/settings', payload);
}
