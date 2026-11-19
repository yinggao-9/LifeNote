import client from './client';

export function getSummary(month) {
  return client.get('/summary', { params: { month } });
}
