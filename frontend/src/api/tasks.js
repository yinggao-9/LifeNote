import client from './client';

// Task endpoints. PATCH semantics: fields left null are unchanged.

export function listTasks({ from, to, status } = {}) {
  return client.get('/tasks', { params: { from, to, status } });
}

export function getTask(id) {
  return client.get(`/tasks/${id}`);
}

export function createTask(payload) {
  return client.post('/tasks', payload);
}

export function updateTask(id, payload) {
  return client.patch(`/tasks/${id}`, payload);
}

export function setTaskStatus(id, status) {
  return client.post(`/tasks/${id}/status`, { status });
}

export function postponeTask(id, payload) {
  return client.post(`/tasks/${id}/postpone`, payload);
}

export function quickAdd(text) {
  return client.post('/tasks/quick-add', { text });
}

// ICS export returns raw calendar bytes, not the JSON envelope.
export function exportIcs({ from, to } = {}) {
  return client.get('/tasks/export.ics', {
    params: { from, to },
    responseType: 'blob',
  });
}
