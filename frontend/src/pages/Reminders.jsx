import { useCallback, useEffect, useState } from 'react';
import { listReminders, markReminderRead } from '../api/reminders';
import { ApiError } from '../api/client';
import { messageForCode } from '../utils/errors';
import { formatDateDisplay, formatInstant } from '../utils/format';
import StatusBadge from '../components/StatusBadge';
import Spinner from '../components/Spinner';
import EmptyState from '../components/EmptyState';
import ErrorBanner from '../components/ErrorBanner';

// Reminder inbox. Backend orders unread items first when showing all.
export default function Reminders() {
  const [unreadOnly, setUnreadOnly] = useState(false);
  const [items, setItems] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const load = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      setItems(await listReminders(unreadOnly));
    } catch (err) {
      setError(err instanceof ApiError ? messageForCode(err.code, err.message) : 'Failed to load reminders.');
    } finally {
      setLoading(false);
    }
  }, [unreadOnly]);

  useEffect(() => {
    load();
  }, [load]);

  async function handleRead(id) {
    setError('');
    try {
      await markReminderRead(id);
      // Under "Unread only" the row no longer matches the filter.
      setItems((prev) =>
        unreadOnly ? prev.filter((r) => r.id !== id) : prev.map((r) => (r.id === id ? { ...r, read: true } : r))
      );
    } catch (err) {
      setError(err instanceof ApiError ? messageForCode(err.code, err.message) : 'Failed to mark as read.');
    }
  }

  return (
    <div>
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-slate-900">Reminders</h1>
          <p className="mt-1 text-sm text-slate-500">
            Deadline alerts and evening digests generated for your tasks.
          </p>
        </div>
        <label className="flex items-center gap-2 text-sm text-slate-600">
          <input
            type="checkbox"
            checked={unreadOnly}
            onChange={(e) => setUnreadOnly(e.target.checked)}
            className="h-4 w-4 rounded border-slate-300 text-brand-600"
          />
          Unread only
        </label>
      </div>

      <div className="mt-4">
        <ErrorBanner message={error} onDismiss={() => setError('')} />
      </div>

      {loading ? (
        <Spinner />
      ) : items.length === 0 ? (
        <div className="mt-6">
          <EmptyState title="No reminders" hint="Reminders appear one day before a due date and as evening digests." />
        </div>
      ) : (
        <div className="mt-6 space-y-3">
          {items.map((item) => (
            <div
              key={item.id}
              className={`rounded-xl border p-4 ${
                item.read ? 'border-slate-200 bg-white' : 'border-brand-200 bg-brand-50'
              }`}
            >
              <div className="flex flex-wrap items-start justify-between gap-3">
                <div>
                  <div className="flex items-center gap-2">
                    <StatusBadge value={item.type} />
                    <h2 className="text-sm font-semibold text-slate-900">{item.title}</h2>
                  </div>
                  <p className="mt-2 whitespace-pre-wrap text-sm text-slate-700">{item.body}</p>
                  <p className="mt-2 text-xs text-slate-400">
                    For {formatDateDisplay(item.remindForDate)} - created {formatInstant(item.createdAt)}
                  </p>
                </div>
                {!item.read && (
                  <button
                    type="button"
                    onClick={() => handleRead(item.id)}
                    className="rounded-lg border border-slate-300 px-3 py-1.5 text-xs text-slate-700 hover:bg-white"
                  >
                    Mark as read
                  </button>
                )}
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
