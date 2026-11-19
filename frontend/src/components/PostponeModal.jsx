import { useEffect, useState } from 'react';
import ErrorBanner from './ErrorBanner';
import { ApiError } from '../api/client';
import { messageForCode } from '../utils/errors';
import { fullTime, todayString } from '../utils/format';

// Postpone dialog. The backend requires the new date to be in the future
// and different from the current due date; it keeps the old task as history
// and creates a copy on the new date.
export default function PostponeModal({ open, task, onClose, onDone, postpone }) {
  const [dueDate, setDueDate] = useState('');
  const [dueTime, setDueTime] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  // Reset stale input every time the dialog opens.
  useEffect(() => {
    if (open) {
      setDueDate('');
      setDueTime('');
      setError('');
    }
  }, [open]);

  if (!open) return null;

  async function handleSubmit(e) {
    e.preventDefault();
    setError('');
    setBusy(true);
    try {
      const payload = { dueDate, dueTime: fullTime(dueTime) };
      const created = await postpone(task.id, payload);
      onDone(created);
    } catch (err) {
      setError(err instanceof ApiError ? messageForCode(err.code, err.message) : 'Request failed.');
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/30 p-4">
      <form
        onSubmit={handleSubmit}
        className="w-full max-w-md rounded-xl bg-white p-6 shadow-xl"
      >
        <h2 className="text-lg font-semibold text-slate-900">Postpone task</h2>
        <p className="mt-1 text-sm text-slate-600">
          "{task.title}" is currently due on {task.dueDate}
          {task.dueTime ? ` at ${task.dueTime.slice(0, 5)}` : ''}. The old entry is kept as
          history and a copy moves to the new date.
        </p>
        <ErrorBanner message={error} onDismiss={() => setError('')} />

        <label className="mt-4 block text-sm font-medium text-slate-700">
          New due date
          <input
            type="date"
            value={dueDate}
            min={todayString()}
            onChange={(e) => setDueDate(e.target.value)}
            required
            className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none"
          />
        </label>
        <label className="mt-4 block text-sm font-medium text-slate-700">
          New due time (optional, keeps the original time when empty)
          <input
            type="time"
            value={dueTime}
            onChange={(e) => setDueTime(e.target.value)}
            className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none"
          />
        </label>

        <div className="mt-6 flex justify-end gap-3">
          <button
            type="button"
            onClick={onClose}
            className="rounded-lg border border-slate-300 px-4 py-2 text-sm text-slate-700 hover:bg-slate-50"
          >
            Cancel
          </button>
          <button
            type="submit"
            disabled={busy}
            className="rounded-lg bg-brand-600 px-4 py-2 text-sm text-white hover:bg-brand-700 disabled:opacity-50"
          >
            Postpone
          </button>
        </div>
      </form>
    </div>
  );
}
