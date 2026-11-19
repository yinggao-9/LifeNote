import client from './client';

export function getBoard(date) {
  return client.get('/boards', { params: date ? { date } : {} });
}

export function listPostponements({ from, to } = {}) {
  return client.get('/postponements', { params: { from, to } });
}
