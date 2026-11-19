import client from './client';

// Document endpoints. Submit is multipart and runs extraction synchronously.

export function submitDocument({ file, text }) {
  const form = new FormData();
  if (file) form.append('file', file);
  if (text) form.append('text', text);
  return client.post('/documents', form, { timeout: 120000 });
}

export function listDocuments() {
  return client.get('/documents');
}

export function getDocument(id) {
  return client.get(`/documents/${id}`);
}

export function listCandidates(id) {
  return client.get(`/documents/${id}/candidates`);
}

export function updateCandidate(docId, candidate) {
  return client.put(`/documents/${docId}/candidates/${candidate.id}`, candidate);
}

export function confirmDocument(docId, candidates) {
  return client.post(`/documents/${docId}/confirm`, { candidates });
}

export function cancelDocument(docId) {
  return client.post(`/documents/${docId}/cancel`);
}

export function reextractDocument(docId) {
  return client.post(`/documents/${docId}/reextract`, null, { timeout: 120000 });
}

export function deleteDocument(docId) {
  return client.delete(`/documents/${docId}`);
}
