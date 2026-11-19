import { useCallback, useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import {
  cancelDocument,
  confirmDocument,
  deleteDocument,
  getDocument,
  reextractDocument,
} from '../api/documents';
import { ApiError } from '../api/client';
import { messageForCode } from '../utils/errors';
import { formatInstant } from '../utils/format';
import StatusBadge from '../components/StatusBadge';
import CandidateEditor from '../components/CandidateEditor';
import ConfirmDialog from '../components/ConfirmDialog';
import Spinner from '../components/Spinner';
import ErrorBanner from '../components/ErrorBanner';

// Review page for one document: extracted text, editable AI drafts and
// the confirm / cancel / retry actions.
export default function DocumentDetail() {
  const { id } = useParams();
  const navigate = useNavigate();
  const [doc, setDoc] = useState(null);
  const [candidates, setCandidates] = useState([]);
  const [selectedIds, setSelectedIds] = useState(new Set());
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [dialog, setDialog] = useState(null); // 'cancel' | 'delete'

  const load = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const data = await getDocument(id);
      setDoc(data);
      const cands = data.candidates || [];
      setCandidates(cands);
      setSelectedIds(new Set(cands.map((c) => c.id)));
    } catch (err) {
      setError(err instanceof ApiError ? messageForCode(err.code, err.message) : 'Failed to load the document.');
    } finally {
      setLoading(false);
    }
  }, [id]);

  useEffect(() => {
    load();
  }, [load]);

  function toggleCandidate(cid) {
    setSelectedIds((prev) => {
      const next = new Set(prev);
      if (next.has(cid)) next.delete(cid);
      else next.add(cid);
      return next;
    });
  }

  function updateCandidate(updated) {
    setCandidates((prev) => prev.map((c) => (c.id === updated.id ? updated : c)));
  }

  async function handleConfirm() {
    setBusy(true);
    setError('');
    try {
      // Send only the fields CandidateUpdateRequest accepts.
      const chosen = candidates
        .filter((c) => selectedIds.has(c.id))
        .map((c) => ({
          id: c.id,
          title: c.title,
          dueDate: c.dueDate,
          dueTime: c.dueTime,
          place: c.place,
          documentsToBring: c.documentsToBring,
          notes: c.notes,
        }));
      const res = await confirmDocument(id, chosen);
      setNotice(res.message);
      await load();
    } catch (err) {
      setError(err instanceof ApiError ? messageForCode(err.code, err.message) : 'Confirm failed.');
    } finally {
      setBusy(false);
    }
  }

  async function handleCancel() {
    setBusy(true);
    setError('');
    try {
      await cancelDocument(id);
      setDialog(null);
      setNotice('Cancelled, nothing was saved.');
      await load();
    } catch (err) {
      setError(err instanceof ApiError ? messageForCode(err.code, err.message) : 'Cancel failed.');
    } finally {
      setBusy(false);
    }
  }

  async function handleReextract() {
    setBusy(true);
    setError('');
    setNotice('');
    try {
      await reextractDocument(id);
      setNotice('Re-extraction finished.');
      await load();
    } catch (err) {
      setError(err instanceof ApiError ? messageForCode(err.code, err.message) : 'Re-extraction failed.');
    } finally {
      setBusy(false);
    }
  }

  async function handleDelete() {
    setBusy(true);
    setError('');
    try {
      await deleteDocument(id);
      navigate('/documents');
    } catch (err) {
      setError(err instanceof ApiError ? messageForCode(err.code, err.message) : 'Delete failed.');
      setBusy(false);
      setDialog(null);
    }
  }

  if (loading) return <Spinner />;
  if (!doc) return <ErrorBanner message={error || 'Document not found.'} />;

  const editable = doc.status === 'PENDING_CONFIRM';

  return (
    <div className="mx-auto max-w-4xl">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <div className="flex items-center gap-3">
            <h1 className="text-2xl font-bold text-slate-900">
              {doc.filename || 'Pasted text'}
            </h1>
            <StatusBadge value={doc.docType} />
            <StatusBadge value={doc.status} />
          </div>
          <p className="mt-1 text-sm text-slate-500">Submitted {formatInstant(doc.createdAt)}</p>
        </div>
        <div className="flex flex-wrap gap-2">
          {(doc.status === 'FAILED' || editable) && (
            <button
              type="button"
              onClick={handleReextract}
              disabled={busy}
              className="rounded-lg border border-slate-300 px-3 py-1.5 text-sm text-slate-700 hover:bg-slate-50 disabled:opacity-50"
            >
              {doc.status === 'FAILED' ? 'Retry extraction' : 'Re-extract'}
            </button>
          )}
          {editable && (
            <>
              <button
                type="button"
                onClick={() => setDialog('cancel')}
                disabled={busy}
                className="rounded-lg border border-red-300 px-3 py-1.5 text-sm text-red-600 hover:bg-red-50 disabled:opacity-50"
              >
                Cancel
              </button>
              <button
                type="button"
                onClick={handleConfirm}
                disabled={busy || selectedIds.size === 0}
                className="rounded-lg bg-brand-600 px-3 py-1.5 text-sm text-white hover:bg-brand-700 disabled:opacity-50"
              >
                {busy ? 'Working...' : `Confirm ${selectedIds.size} task(s)`}
              </button>
            </>
          )}
          <button
            type="button"
            onClick={() => setDialog('delete')}
            disabled={busy}
            className="rounded-lg border border-slate-300 px-3 py-1.5 text-sm text-slate-500 hover:bg-slate-50 disabled:opacity-50"
          >
            Delete
          </button>
        </div>
      </div>

      {doc.errorMessage && (
        <div className="mt-4 rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
          {doc.errorMessage}
        </div>
      )}
      {notice && (
        <div className="mt-4 rounded-lg border border-emerald-200 bg-emerald-50 px-4 py-2 text-sm text-emerald-700">
          {notice}{' '}
          {doc.status === 'CONFIRMED' && (
            <Link to="/tasks" className="font-medium underline">
              View tasks
            </Link>
          )}
        </div>
      )}
      <div className="mt-4">
        <ErrorBanner message={error} onDismiss={() => setError('')} />
      </div>

      <div className="mt-6 grid grid-cols-1 gap-6 lg:grid-cols-2">
        <div>
          <div className="rounded-xl border border-slate-200 bg-white p-5">
            <h2 className="text-sm font-semibold text-slate-700">Extracted text</h2>
            <p className="mt-2 max-h-96 overflow-y-auto whitespace-pre-wrap text-sm text-slate-700">
              {doc.rawText || 'No text available.'}
            </p>
          </div>

          {doc.sourceUrl && (
            <div className="mt-4 rounded-xl border border-slate-200 bg-white p-5">
              <h2 className="text-sm font-semibold text-slate-700">Source file</h2>
              <p className="mt-1 text-xs text-slate-400">
                Preview links expire 10 minutes after the page loads.
              </p>
              {doc.docType === 'IMAGE' ? (
                <img src={doc.sourceUrl} alt="Source" className="mt-3 max-h-80 rounded-lg border border-slate-200" />
              ) : (
                <a
                  href={doc.sourceUrl}
                  target="_blank"
                  rel="noreferrer"
                  className="mt-3 inline-block rounded-lg border border-brand-300 px-3 py-1.5 text-sm text-brand-700 hover:bg-brand-50"
                >
                  Open original file
                </a>
              )}
            </div>
          )}
        </div>

        <div>
          <h2 className="text-sm font-semibold text-slate-700">
            Task drafts ({candidates.length})
          </h2>
          {editable && (
            <p className="mt-1 text-xs text-slate-400">
              Review and edit each draft. Unchecked drafts are not saved when you confirm.
            </p>
          )}
          <div className="mt-3 space-y-4">
            {candidates.length === 0 ? (
              <p className="text-sm text-slate-400">No drafts were extracted.</p>
            ) : (
              candidates.map((c) =>
                editable ? (
                  <CandidateEditor
                    key={c.id}
                    candidate={c}
                    selected={selectedIds.has(c.id)}
                    onToggle={() => toggleCandidate(c.id)}
                    onChange={updateCandidate}
                  />
                ) : (
                  <div key={c.id} className="rounded-xl border border-slate-200 bg-white p-4">
                    <div className="flex items-center justify-between">
                      <p className="font-medium text-slate-900">{c.title}</p>
                      <StatusBadge value={c.status} />
                    </div>
                    <p className="mt-1 text-sm text-slate-500">
                      {c.dueDate}
                      {c.dueTime ? ` ${c.dueTime.slice(0, 5)}` : ''}
                      {c.place ? ` - ${c.place}` : ''}
                    </p>
                  </div>
                )
              )
            )}
          </div>
        </div>
      </div>

      <ConfirmDialog
        open={dialog === 'cancel'}
        title="Cancel this document?"
        message="All extracted drafts will be deleted. Nothing is saved to your task list."
        confirmLabel="Discard drafts"
        danger
        busy={busy}
        onConfirm={handleCancel}
        onCancel={() => setDialog(null)}
      />
      <ConfirmDialog
        open={dialog === 'delete'}
        title="Delete this document?"
        message="The uploaded file and drafts are removed. Tasks already created from it are kept."
        confirmLabel="Delete"
        danger
        busy={busy}
        onConfirm={handleDelete}
        onCancel={() => setDialog(null)}
      />
    </div>
  );
}
