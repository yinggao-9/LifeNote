import client from './client';

export function listReminders(unreadOnly = false) {
  return client.get('/reminders', { params: { unreadOnly } });
}

export function markReminderRead(id) {
  return client.post(`/reminders/${id}/read`);
}
