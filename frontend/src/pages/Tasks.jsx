import { useCallback, useEffect, useState } from 'react';
import { createTask, exportIcs, listTasks, quickAdd } from '../api/tasks';
import { ApiError } from '../api/client';
import { messageForCode } from '../utils/errors';
import {
  daysInMonth,
  formatDateDisplay,
  monthString,
  parseDate,
  todayString,
} from '../utils/format';
import TaskCard from '../components/TaskCard';
import TaskFormModal from '../components/TaskFormModal';
import Spinner from '../components/Spinner';
import EmptyState from '../components/EmptyState';
import ErrorBanner from '../components/ErrorBanner';

const STATUS_OPTIONS = ['', 'TODO', 'DONE', 'CANCELLED', 'POSTPONED'];
const WEEKDAYS = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'];

// Tasks page: quick-add bar, list view (range + status filter) and
// a calendar view over a month.
export default function Tasks() {
  const [view, setView] = useState('list');

  // List view state. Backend defaults to "today only" when range is omitted.
  const [from, setFrom] = useState(todayString());
  const [to, setTo] = useState(todayString());
  const [status, setStatus] = useState('');
  const [tasks, setTasks] = useState([]);

  // Calendar view state.
  const [month, setMonth] = useState(monthString(new Date()));
  const [monthTasks, setMonthTasks] = useState([]);
  const [selectedDay, setSelectedDay] = useState(null);

  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');

  const [quickText, setQuickText] = useState('');
  const [quickBusy, setQuickBusy] = useState(false);
  const [formOpen, setFormOpen] = useState(false);
  const [formInitial, setFormInitial] = useState(null);
  const [formBusy, setFormBusy] = useState(false);

  const loadList = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const rows = await listTasks({ from, to, status: status || undefined });
      setTasks(rows);
    } catch (err) {
      setError(err instanceof ApiError ? messageForCode(err.code, err.message) : 'Failed to load tasks.');
    } finally {
      setLoading(false);
    }
  }, [from, to, status]);

  const loadMonth = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const days = daysInMonth(month);
      const rows = await listTasks({ from: days[0], to: days[days.length - 1] });
      setMonthTasks(rows);
    } catch (err) {
      setError(err instanceof ApiError ? messageForCode(err.code, err.message) : 'Failed to load tasks.');
    } finally {
      setLoading(false);
    }
  }, [month]);

  useEffect(() => {
    if (view === 'list') loadList();
    else loadMonth();
  }, [view, loadList, loadMonth]);

  async function handleQuickAdd(e) {
    e.preventDefault();
    if (!quickText.trim()) return;
    setQuickBusy(true);
    setError('');
    setNotice('');
    try {
      const res = await quickAdd(quickText.trim());
      if (res.status === 'OK') {
        setQuickText('');
        setNotice('Task created.');
        if (view === 'list') loadList();
        else loadMonth();
      } else {
        // Parser could not fill every field; open the form pre-filled
        // with whatever it did recognize.
        setFormInitial({
          title: res.title || '',
          dueDate: res.dueDate || todayString(),
          dueTime: res.dueTime || '',
        });
        setFormOpen(true);
      }
    } catch (err) {
      setError(err instanceof ApiError ? messageForCode(err.code, err.message) : 'Quick add failed.');
    } finally {
      setQuickBusy(false);
    }
  }

  async function handleCreate(payload) {
    setFormBusy(true);
    try {
      await createTask(payload);
      setFormOpen(false);
      setFormInitial(null);
      setNotice('Task created.');
      if (view === 'list') loadList();
      else loadMonth();
    } finally {
      setFormBusy(false);
    }
  }

  async function handleExport() {
    setError('');
    try {
      const range =
        view === 'list'
          ? { from, to }
          : { from: daysInMonth(month)[0], to: daysInMonth(month).slice(-1)[0] };
      const blob = await exportIcs(range);
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = 'lifenote.ics';
      a.click();
      URL.revokeObjectURL(url);
    } catch (err) {
      setError(err instanceof ApiError ? messageForCode(err.code, err.message) : 'Export failed.');
    }
  }

  // Group month tasks by due date for the calendar cells.
  const byDay = {};
  for (const task of monthTasks) {
    (byDay[task.dueDate] ||= []).push(task);
  }
  const dayList = daysInMonth(month);
  const firstWeekday = (parseDate(dayList[0]).getDay() + 6) % 7; // Monday-first offset.
  const monthDate = parseDate(`${month}-01`);
  const selectedTasks = selectedDay ? byDay[selectedDay] || [] : [];

  return (
    <div>
      <div className="flex flex-wrap items-center justify-between gap-4">
        <h1 className="text-2xl font-bold text-slate-900">Tasks</h1>
        <div className="flex items-center gap-2">
          <div className="flex rounded-lg border border-slate-300 p-0.5">
            {['list', 'calendar'].map((v) => (
              <button
                key={v}
                type="button"
                onClick={() => setView(v)}
                className={`rounded-md px-3 py-1.5 text-sm capitalize ${
                  view === v ? 'bg-brand-600 text-white' : 'text-slate-600 hover:bg-slate-100'
                }`}
              >
                {v}
              </button>
            ))}
          </div>
          <button
            type="button"
            onClick={handleExport}
            className="rounded-lg border border-slate-300 px-3 py-1.5 text-sm text-slate-700 hover:bg-slate-50"
          >
            Export .ics
          </button>
          <button
            type="button"
            onClick={() => {
              setFormInitial(null);
              setFormOpen(true);
            }}
            className="rounded-lg bg-brand-600 px-3 py-1.5 text-sm text-white hover:bg-brand-700"
          >
            New task
          </button>
        </div>
      </div>

      {/* Quick-add: one sentence parsed locally by the backend. */}
      <form onSubmit={handleQuickAdd} className="mt-4 flex gap-2">
        <input
          type="text"
          value={quickText}
          onChange={(e) => setQuickText(e.target.value)}
          maxLength={500}
          placeholder='Quick add, e.g. "buy milk tomorrow at 10am"'
          className="flex-1 rounded-lg border border-slate-300 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none"
        />
        <button
          type="submit"
          disabled={quickBusy}
          className="rounded-lg bg-slate-800 px-4 py-2 text-sm text-white hover:bg-slate-900 disabled:opacity-50"
        >
          {quickBusy ? 'Adding...' : 'Add'}
        </button>
      </form>

      {notice && (
        <div className="mt-4 rounded-lg border border-emerald-200 bg-emerald-50 px-4 py-2 text-sm text-emerald-700">
          {notice}
        </div>
      )}
      <div className="mt-4">
        <ErrorBanner message={error} onDismiss={() => setError('')} />
      </div>

      {view === 'list' ? (
        <>
          <div className="mt-2 flex flex-wrap items-end gap-3">
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
            <label className="text-sm font-medium text-slate-700">
              Status
              <select
                value={status}
                onChange={(e) => setStatus(e.target.value)}
                className="ml-2 rounded-lg border border-slate-300 px-2 py-1.5 text-sm focus:border-brand-500 focus:outline-none"
              >
                <option value="">All</option>
                {STATUS_OPTIONS.filter(Boolean).map((s) => (
                  <option key={s} value={s}>
                    {s}
                  </option>
                ))}
              </select>
            </label>
          </div>

          {loading ? (
            <Spinner />
          ) : tasks.length === 0 ? (
            <div className="mt-6">
              <EmptyState title="No tasks in this range" hint="Try widening the date range." />
            </div>
          ) : (
            <div className="mt-6 grid grid-cols-1 gap-3 md:grid-cols-2 xl:grid-cols-3">
              {tasks.map((task) => (
                <TaskCard key={task.id} task={task} />
              ))}
            </div>
          )}
        </>
      ) : (
        <>
          <div className="mt-4 flex items-center gap-3">
            <button
              type="button"
              onClick={() => {
                const d = parseDate(`${month}-01`);
                d.setMonth(d.getMonth() - 1);
                setMonth(monthString(d));
                setSelectedDay(null);
              }}
              className="rounded-lg border border-slate-300 px-3 py-1.5 text-sm text-slate-700 hover:bg-slate-50"
            >
              Prev
            </button>
            <span className="text-sm font-medium text-slate-700">
              {monthDate.toLocaleDateString(undefined, { month: 'long', year: 'numeric' })}
            </span>
            <button
              type="button"
              onClick={() => {
                const d = parseDate(`${month}-01`);
                d.setMonth(d.getMonth() + 1);
                setMonth(monthString(d));
                setSelectedDay(null);
              }}
              className="rounded-lg border border-slate-300 px-3 py-1.5 text-sm text-slate-700 hover:bg-slate-50"
            >
              Next
            </button>
          </div>

          {loading ? (
            <Spinner />
          ) : (
            <>
              <div className="mt-4 grid grid-cols-7 gap-px overflow-hidden rounded-xl border border-slate-200 bg-slate-200">
                {WEEKDAYS.map((d) => (
                  <div key={d} className="bg-white px-2 py-1.5 text-center text-xs font-medium text-slate-500">
                    {d}
                  </div>
                ))}
                {Array.from({ length: firstWeekday }).map((_, i) => (
                  <div key={`pad-${i}`} className="bg-slate-50 p-2" />
                ))}
                {dayList.map((day) => {
                  const items = byDay[day] || [];
                  const isToday = day === todayString();
                  return (
                    <button
                      key={day}
                      type="button"
                      onClick={() => setSelectedDay(day === selectedDay ? null : day)}
                      className={`min-h-20 bg-white p-2 text-left align-top hover:bg-brand-50 ${
                        selectedDay === day ? 'ring-2 ring-brand-500' : ''
                      }`}
                    >
                      <span
                        className={`inline-flex h-6 w-6 items-center justify-center rounded-full text-xs ${
                          isToday ? 'bg-brand-600 text-white' : 'text-slate-600'
                        }`}
                      >
                        {parseDate(day).getDate()}
                      </span>
                      <div className="mt-1 space-y-0.5">
                        {items.slice(0, 3).map((task) => (
                          <div
                            key={task.id}
                            className={`truncate rounded px-1 text-xs ${
                              task.status === 'DONE'
                                ? 'bg-emerald-50 text-emerald-700'
                                : task.status === 'CANCELLED'
                                  ? 'bg-slate-100 text-slate-500 line-through'
                                  : task.status === 'POSTPONED'
                                    ? 'bg-amber-50 text-amber-700'
                                    : 'bg-blue-50 text-blue-700'
                            }`}
                          >
                            {task.title}
                          </div>
                        ))}
                        {items.length > 3 && (
                          <div className="text-xs text-slate-400">+{items.length - 3} more</div>
                        )}
                      </div>
                    </button>
                  );
                })}
              </div>

              {selectedDay && (
                <div className="mt-6">
                  <h2 className="text-sm font-semibold text-slate-700">
                    {formatDateDisplay(selectedDay)}
                  </h2>
                  <div className="mt-2 grid grid-cols-1 gap-3 md:grid-cols-2 xl:grid-cols-3">
                    {selectedTasks.length === 0 ? (
                      <p className="text-sm text-slate-400">No tasks on this day.</p>
                    ) : (
                      selectedTasks.map((task) => <TaskCard key={task.id} task={task} />)
                    )}
                  </div>
                </div>
              )}
            </>
          )}
        </>
      )}

      <TaskFormModal
        open={formOpen}
        initial={formInitial}
        busy={formBusy}
        onClose={() => {
          setFormOpen(false);
          setFormInitial(null);
        }}
        onSubmit={handleCreate}
      />
    </div>
  );
}
