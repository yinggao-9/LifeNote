import { useCallback, useEffect, useState } from 'react';
import { getBoard } from '../api/boards';
import { ApiError } from '../api/client';
import { messageForCode } from '../utils/errors';
import { todayString, formatDateDisplay, parseDate, toDateString } from '../utils/format';
import TaskCard from '../components/TaskCard';
import Spinner from '../components/Spinner';
import EmptyState from '../components/EmptyState';
import ErrorBanner from '../components/ErrorBanner';

const COLUMNS = [
  { key: 'todoTasks', label: 'To do', accent: 'border-t-blue-500' },
  { key: 'doneTasks', label: 'Done', accent: 'border-t-emerald-500' },
  { key: 'cancelledTasks', label: 'Cancelled', accent: 'border-t-slate-400' },
  { key: 'postponedTasks', label: 'Postponed', accent: 'border-t-amber-500' },
];

// Day board: one day of tasks in four status columns plus a progress bar.
// Note: the backend's "total" excludes postponed tasks.
export default function Board() {
  const [date, setDate] = useState(todayString);
  const [board, setBoard] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const load = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      setBoard(await getBoard(date));
    } catch (err) {
      setError(err instanceof ApiError ? messageForCode(err.code, err.message) : 'Failed to load the board.');
    } finally {
      setLoading(false);
    }
  }, [date]);

  useEffect(() => {
    load();
  }, [load]);

  const total = board?.total ?? 0;
  const done = board?.done ?? 0;
  const pct = total > 0 ? Math.round((done / total) * 100) : 0;

  return (
    <div>
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-slate-900">Day board</h1>
          <p className="mt-1 text-sm text-slate-500">{formatDateDisplay(date)}</p>
        </div>
        <div className="flex items-center gap-3">
          <button
            type="button"
            onClick={() => setDate((d) => {
              const prev = parseDate(d);
              prev.setDate(prev.getDate() - 1);
              return toDateString(prev);
            })}
            className="rounded-lg border border-slate-300 px-3 py-1.5 text-sm text-slate-700 hover:bg-slate-50"
          >
            Prev
          </button>
          <input
            type="date"
            value={date}
            onChange={(e) => setDate(e.target.value)}
            className="rounded-lg border border-slate-300 px-3 py-1.5 text-sm focus:border-brand-500 focus:outline-none"
          />
          <button
            type="button"
            onClick={() => setDate(todayString())}
            className="rounded-lg border border-slate-300 px-3 py-1.5 text-sm text-slate-700 hover:bg-slate-50"
          >
            Today
          </button>
          <button
            type="button"
            onClick={() => setDate((d) => {
              const next = parseDate(d);
              next.setDate(next.getDate() + 1);
              return toDateString(next);
            })}
            className="rounded-lg border border-slate-300 px-3 py-1.5 text-sm text-slate-700 hover:bg-slate-50"
          >
            Next
          </button>
        </div>
      </div>

      <ErrorBanner message={error} onDismiss={() => setError('')} />

      {loading ? (
        <Spinner />
      ) : board ? (
        <>
          <div className="mt-6 rounded-xl border border-slate-200 bg-white p-4">
            <div className="flex items-center justify-between text-sm text-slate-600">
              <span>
                {done} of {total} done ({pct}%)
              </span>
              <span>
                {board.postponed} postponed
              </span>
            </div>
            <div className="mt-2 h-2 w-full overflow-hidden rounded-full bg-slate-100">
              <div
                className="h-full rounded-full bg-emerald-500 transition-all"
                style={{ width: `${pct}%` }}
              />
            </div>
          </div>

          <div className="mt-6 grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-4">
            {COLUMNS.map((col) => {
              const items = board[col.key] || [];
              return (
                <div
                  key={col.key}
                  className={`rounded-xl border border-slate-200 border-t-4 ${col.accent} bg-white p-3`}
                >
                  <div className="flex items-center justify-between px-1 pb-2">
                    <h2 className="text-sm font-semibold text-slate-700">{col.label}</h2>
                    <span className="text-xs text-slate-400">{items.length}</span>
                  </div>
                  <div className="space-y-2">
                    {items.length === 0 ? (
                      <p className="px-1 py-4 text-center text-xs text-slate-400">Empty</p>
                    ) : (
                      items.map((task) => <TaskCard key={task.id} task={task} />)
                    )}
                  </div>
                </div>
              );
            })}
          </div>
        </>
      ) : (
        <EmptyState title="No data for this day" />
      )}
    </div>
  );
}
