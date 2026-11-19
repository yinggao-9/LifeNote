import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { listPostponements } from '../api/boards';
import { ApiError } from '../api/client';
import { messageForCode } from '../utils/errors';
import { formatDateDisplay, formatInstant, shortTime, toDateString } from '../utils/format';
import Spinner from '../components/Spinner';
import EmptyState from '../components/EmptyState';
import ErrorBanner from '../components/ErrorBanner';

// Postpone log: every postpone action keeps the old task as history and
// creates a copy. Rows are filtered by the OLD due date.
export default function Postponements() {
  const now = new Date();
  const firstOfMonth = toDateString(new Date(now.getFullYear(), now.getMonth(), 1));
  const lastOfMonth = toDateString(new Date(now.getFullYear(), now.getMonth() + 1, 0));

  const [from, setFrom] = useState(firstOfMonth);
  const [to, setTo] = useState(lastOfMonth);
  const [items, setItems] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const load = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      setItems(await listPostponements({ from, to }));
    } catch (err) {
      setError(err instanceof ApiError ? messageForCode(err.code, err.message) : 'Failed to load postponements.');
    } finally {
      setLoading(false);
    }
  }, [from, to]);

  useEffect(() => {
    load();
  }, [load]);

  return (
    <div>
      <h1 className="text-2xl font-bold text-slate-900">Postponements</h1>
      <p className="mt-1 text-sm text-slate-500">
        History of moved tasks, filtered by the original due date.
      </p>

      <div className="mt-4 flex flex-wrap items-end gap-3">
        <label className="text-sm font-medium text-slate-700">
          From
          <input
            type="date"
            value={from}
            onChange={(e) => setFrom(e.target.value)}
            className="ml-2 rounded-lg border border-slate-300 px-2 py-1.5 text-sm focus:border-brand-500 focus:outline-none"
          />
        </label>
        <label className="text-sm font-medium text-slate-700">
          To
          <input
            type="date"
            value={to}
            onChange={(e) => setTo(e.target.value)}
            className="ml-2 rounded-lg border border-slate-300 px-2 py-1.5 text-sm focus:border-brand-500 focus:outline-none"
          />
        </label>
      </div>

      <div className="mt-4">
        <ErrorBanner message={error} onDismiss={() => setError('')} />
      </div>

      {loading ? (
        <Spinner />
      ) : items.length === 0 ? (
        <div className="mt-6">
          <EmptyState title="No postponements in this range" />
        </div>
      ) : (
        <div className="mt-6 overflow-hidden rounded-xl border border-slate-200 bg-white">
          <table className="min-w-full text-sm">
            <thead>
              <tr className="border-b border-slate-200 bg-slate-50 text-left text-xs font-medium text-slate-500">
                <th className="px-4 py-3">Moved from</th>
                <th className="px-4 py-3">Moved to</th>
                <th className="px-4 py-3">Recorded</th>
                <th className="px-4 py-3">Links</th>
              </tr>
            </thead>
            <tbody>
              {items.map((row) => (
                <tr key={row.id} className="border-b border-slate-100">
                  <td className="px-4 py-3 text-slate-700">
                    {formatDateDisplay(row.oldDueDate)}
                    {row.oldDueTime ? ` ${shortTime(row.oldDueTime)}` : ''}
                  </td>
                  <td className="px-4 py-3 text-slate-700">
                    {formatDateDisplay(row.newDueDate)}
                    {row.newDueTime ? ` ${shortTime(row.newDueTime)}` : ''}
                  </td>
                  <td className="px-4 py-3 text-slate-500">{formatInstant(row.createdAt)}</td>
                  <td className="px-4 py-3">
                    <div className="flex gap-2">
                      <Link
                        to={`/tasks/${row.taskId}`}
                        className="rounded-lg border border-slate-300 px-2 py-1 text-xs text-slate-600 hover:bg-slate-50"
                      >
                        Old entry
                      </Link>
                      <Link
                        to={`/tasks/${row.newTaskId}`}
                        className="rounded-lg border border-brand-300 px-2 py-1 text-xs text-brand-700 hover:bg-brand-50"
                      >
                        New copy
                      </Link>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
