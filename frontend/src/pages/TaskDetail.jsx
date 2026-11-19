import { useCallback, useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { getTask, postponeTask, setTaskStatus, updateTask } from '../api/tasks';
import { ApiError } from '../api/client';
import { messageForCode } from '../utils/errors';
import { formatDateDisplay, formatInstant, shortTime } from '../utils/format';
import StatusBadge from '../components/StatusBadge';
import TaskFormModal from '../components/TaskFormModal';
import PostponeModal from '../components/PostponeModal';
import Spinner from '../components/Spinner';
import ErrorBanner from '../components/ErrorBanner';

// Task detail: full fields, source documents, status actions, postpone.
export default function TaskDetail() {
  const { id } = useParams();
  const navigate = useNavigate();
  const [task, setTask] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [editOpen, setEditOpen] = useState(false);
  const [postponeOpen, setPostponeOpen] = useState(false);
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      setTask(await getTask(id));
    } catch (err) {
      setError(err instanceof ApiError ? messageForCode(err.code, err.message) : 'Failed to load the task.');
    } finally {
      setLoading(false);
    }
  }, [id]);

  useEffect(() => {
    load();
  }, [load]);

  async function changeStatus(status) {
    setBusy(true);
    setError('');
    try {
      // TaskResponse from this endpoint has no sources; keep the loaded ones.
      const updated = await setTaskStatus(id, status);
      setTask((prev) => ({ ...updated, sources: prev.sources }));
      setNotice(status === 'DONE' ? 'Marked as done.' : 'Status updated.');
    } catch (err) {
      setError(err instanceof ApiError ? messageForCode(err.code, err.message) : 'Status change failed.');
    } finally {
      setBusy(false);
    }
  }

  async function handleEdit(payload) {
    setBusy(true);
    try {
      const updated = await updateTask(id, payload);
      setTask((prev) => ({ ...updated, sources: prev.sources }));
      setEditOpen(false);
      setNotice('Changes saved.');
    } finally {
      setBusy(false);
    }
  }

  async function handlePostponeDone(newCopy) {
    setPostponeOpen(false);
    // Postpone creates a copy on the new date; show that copy.
    navigate(`/tasks/${newCopy.id}`);
    setNotice('Task postponed. You are now viewing the new copy.');
  }

  if (loading) return <Spinner />;
  if (!task) {
    return <ErrorBanner message={error || 'Task not found.'} />;
  }

  return (
    <div className="mx-auto max-w-3xl">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <div className="flex items-center gap-3">
            <h1 className="text-2xl font-bold text-slate-900">{task.title}</h1>
            <StatusBadge value={task.status} />
            <StatusBadge value={task.source} />
          </div>
          <p className="mt-1 text-sm text-slate-500">
            Due {formatDateDisplay(task.dueDate)}
            {task.dueTime ? ` at ${shortTime(task.dueTime)}` : ''}
          </p>
        </div>
        <div className="flex flex-wrap gap-2">
          <button
            type="button"
            onClick={() => setEditOpen(true)}
            className="rounded-lg border border-slate-300 px-3 py-1.5 text-sm text-slate-700 hover:bg-slate-50"
          >
            Edit
          </button>
          {task.status !== 'DONE' && (
            <button
              type="button"
              disabled={busy}
              onClick={() => changeStatus('DONE')}
              className="rounded-lg bg-emerald-600 px-3 py-1.5 text-sm text-white hover:bg-emerald-700 disabled:opacity-50"
            >
              Mark done
            </button>
          )}
          {task.status === 'DONE' && (
            <button
              type="button"
              disabled={busy}
              onClick={() => changeStatus('TODO')}
              className="rounded-lg bg-brand-600 px-3 py-1.5 text-sm text-white hover:bg-brand-700 disabled:opacity-50"
            >
              Reopen
            </button>
          )}
          {task.status !== 'CANCELLED' && (
            <button
              type="button"
              disabled={busy}
              onClick={() => changeStatus('CANCELLED')}
              className="rounded-lg border border-red-300 px-3 py-1.5 text-sm text-red-600 hover:bg-red-50 disabled:opacity-50"
            >
              Cancel task
            </button>
          )}
          {task.status !== 'POSTPONED' && task.status !== 'CANCELLED' && (
            <button
              type="button"
              onClick={() => setPostponeOpen(true)}
              className="rounded-lg border border-amber-300 px-3 py-1.5 text-sm text-amber-700 hover:bg-amber-50"
            >
              Postpone
            </button>
          )}
        </div>
      </div>

      {notice && (
        <div className="mt-4 rounded-lg border border-emerald-200 bg-emerald-50 px-4 py-2 text-sm text-emerald-700">
          {notice}
        </div>
      )}
      <div className="mt-4">
        <ErrorBanner message={error} onDismiss={() => setError('')} />
      </div>

      {task.postponedFromTaskId && (
        <div className="mt-4 rounded-lg border border-amber-200 bg-amber-50 px-4 py-2 text-sm text-amber-800">
          This task was postponed from an earlier date.{' '}
          <Link to={`/tasks/${task.postponedFromTaskId}`} className="font-medium underline">
            View the original entry
          </Link>
        </div>
      )}

      <div className="mt-6 grid grid-cols-1 gap-4 md:grid-cols-2">
        <div className="rounded-xl border border-slate-200 bg-white p-5">
          <h2 className="text-sm font-semibold text-slate-700">Details</h2>
          <dl className="mt-3 space-y-2 text-sm">
            <div className="flex justify-between gap-4">
              <dt className="text-slate-500">Place</dt>
              <dd className="text-slate-900">{task.place || '-'}</dd>
            </div>
            <div className="flex justify-between gap-4">
              <dt className="text-slate-500">Completed at</dt>
              <dd className="text-slate-900">{formatInstant(task.completedAt) || '-'}</dd>
            </div>
            <div className="flex justify-between gap-4">
              <dt className="text-slate-500">Created at</dt>
              <dd className="text-slate-900">{formatInstant(task.createdAt)}</dd>
            </div>
          </dl>

          <h3 className="mt-5 text-sm font-semibold text-slate-700">Documents to bring</h3>
          {task.documentsToBring && task.documentsToBring.length > 0 ? (
            <ul className="mt-2 list-inside list-disc space-y-1 text-sm text-slate-700">
              {task.documentsToBring.map((doc, i) => (
                <li key={i}>{doc}</li>
              ))}
            </ul>
          ) : (
            <p className="mt-2 text-sm text-slate-400">None listed.</p>
          )}

          <h3 className="mt-5 text-sm font-semibold text-slate-700">Notes</h3>
          <p className="mt-2 whitespace-pre-wrap text-sm text-slate-700">{task.notes || '-'}</p>
        </div>

        <div className="rounded-xl border border-slate-200 bg-white p-5">
          <h2 className="text-sm font-semibold text-slate-700">Source material</h2>
          <p className="mt-1 text-xs text-slate-400">
            File links are temporary and expire 10 minutes after loading.
          </p>
          {task.sources && task.sources.length > 0 ? (
            <ul className="mt-3 space-y-2">
              {task.sources.map((src) => (
                <li
                  key={src.documentId}
                  className="flex items-center justify-between rounded-lg border border-slate-200 px-3 py-2 text-sm"
                >
                  <div>
                    <p className="font-medium text-slate-800">{src.filename || 'Pasted text'}</p>
                    <p className="text-xs text-slate-400">{src.contentType}</p>
                  </div>
                  <div className="flex gap-2">
                    <Link
                      to={`/documents/${src.documentId}`}
                      className="rounded-lg border border-slate-300 px-2 py-1 text-xs text-slate-600 hover:bg-slate-50"
                    >
                      Document
                    </Link>
                    {src.url && (
                      <a
                        href={src.url}
                        target="_blank"
                        rel="noreferrer"
                        className="rounded-lg border border-brand-300 px-2 py-1 text-xs text-brand-700 hover:bg-brand-50"
                      >
                        Open file
                      </a>
                    )}
                  </div>
                </li>
              ))}
            </ul>
          ) : (
            <p className="mt-3 text-sm text-slate-400">No linked documents.</p>
          )}
        </div>
      </div>

      <TaskFormModal
        open={editOpen}
        initial={task}
        busy={busy}
        onClose={() => setEditOpen(false)}
        onSubmit={handleEdit}
      />
      <PostponeModal
        open={postponeOpen}
        task={task}
        postpone={postponeTask}
        onClose={() => setPostponeOpen(false)}
        onDone={handlePostponeDone}
      />
    </div>
  );
}
