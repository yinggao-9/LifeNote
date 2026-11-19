import { useEffect, useState } from 'react';
import ErrorBanner from './ErrorBanner';
import { ApiError } from '../api/client';
import { formatValidationErrors, messageForCode } from '../utils/errors';
import { fullTime, todayString } from '../utils/format';

// Modal form shared by task creation and task editing.
// For edits the payload follows PATCH semantics: empty fields are omitted,
// so the backend keeps the previous value.
export default function TaskFormModal({ open, initial, onClose, onSubmit, busy }) {
  const isEdit = Boolean(initial && initial.id);
  const [title, setTitle] = useState(initial?.title || '');
  const [dueDate, setDueDate] = useState(initial?.dueDate || todayString());
  const [dueTime, setDueTime] = useState(initial?.dueTime ? initial.dueTime.slice(0, 5) : '');
  const [place, setPlace] = useState(initial?.place || '');
  const [docsText, setDocsText] = useState((initial?.documentsToBring || []).join('\n'));
  const [notes, setNotes] = useState(initial?.notes || '');
  const [error, setError] = useState('');

  // Re-seed the form every time the modal opens with new initial values.
  useEffect(() => {
    if (open) {
      setTitle(initial?.title || '');
      setDueDate(initial?.dueDate || todayString());
      setDueTime(initial?.dueTime ? initial.dueTime.slice(0, 5) : '');
      setPlace(initial?.place || '');
      setDocsText((initial?.documentsToBring || []).join('\n'));
      setNotes(initial?.notes || '');
      setError('');
    }
  }, [open, initial]);

  if (!open) return null;

  async function handleSubmit(e) {
    e.preventDefault();
    setError('');
    const payload = {
      title: title.trim(),
      dueDate,
      dueTime: fullTime(dueTime),
      // Non-null values replace the old ones on edit, so empty strings
      // clear these fields. A cleared time keeps the old time.
      place: place.trim(),
      documentsToBring: docsText
        .split('\n')
        .map((s) => s.trim())
        .filter(Boolean),
      notes: notes.trim(),
    };
    if (!payload.title) {
      setError('Title is required.');
      return;
    }
    try {
      await onSubmit(payload);
    } catch (err) {
      if (err instanceof ApiError && err.code === 400 && err.data) {
        setError(formatValidationErrors(err.data));
      } else if (err instanceof ApiError) {
        setError(messageForCode(err.code, err.message));
      } else {
        setError('Request failed.');
      }
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/30 p-4">
      <form
        onSubmit={handleSubmit}
        className="max-h-[90vh] w-full max-w-lg overflow-y-auto rounded-xl bg-white p-6 shadow-xl"
      >
        <h2 className="text-lg font-semibold text-slate-900">
          {isEdit ? 'Edit task' : 'New task'}
        </h2>
        {isEdit && (
          <p className="mt-1 text-xs text-slate-500">
            Empty place and notes clear those fields. An empty time keeps the previous time.
          </p>
        )}
        <ErrorBanner message={error} onDismiss={() => setError('')} />

        <label className="mt-4 block text-sm font-medium text-slate-700">
          Title
          <input
            type="text"
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            maxLength={200}
            required
            className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none"
          />
        </label>

        <div className="mt-4 grid grid-cols-2 gap-4">
          <label className="block text-sm font-medium text-slate-700">
            Due date
            <input
              type="date"
              value={dueDate}
              onChange={(e) => setDueDate(e.target.value)}
              required
              className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none"
            />
          </label>
          <label className="block text-sm font-medium text-slate-700">
            Due time
            <input
              type="time"
              value={dueTime}
              onChange={(e) => setDueTime(e.target.value)}
              className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none"
            />
          </label>
        </div>

        <label className="mt-4 block text-sm font-medium text-slate-700">
          Place
          <input
            type="text"
            value={place}
            onChange={(e) => setPlace(e.target.value)}
            maxLength={200}
            className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none"
          />
        </label>

        <label className="mt-4 block text-sm font-medium text-slate-700">
          Documents to bring (one per line)
          <textarea
            value={docsText}
            onChange={(e) => setDocsText(e.target.value)}
            rows={3}
            className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none"
          />
        </label>

        <label className="mt-4 block text-sm font-medium text-slate-700">
          Notes
          <textarea
            value={notes}
            onChange={(e) => setNotes(e.target.value)}
            rows={3}
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
            {isEdit ? 'Save changes' : 'Create task'}
          </button>
        </div>
      </form>
    </div>
  );
}
