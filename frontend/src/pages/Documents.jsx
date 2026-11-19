import { useCallback, useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { listDocuments, submitDocument } from '../api/documents';
import { ApiError } from '../api/client';
import { formatValidationErrors, messageForCode } from '../utils/errors';
import { formatInstant } from '../utils/format';
import StatusBadge from '../components/StatusBadge';
import Spinner from '../components/Spinner';
import EmptyState from '../components/EmptyState';
import ErrorBanner from '../components/ErrorBanner';

const ACCEPT = '.pdf,.png,.jpg,.jpeg,.webp';

// Document intake (pasted text or file) plus submission history.
// Extraction runs synchronously on the backend, so the submit request
// can take a while.
export default function Documents() {
  const navigate = useNavigate();
  const [docs, setDocs] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [text, setText] = useState('');
  const [file, setFile] = useState(null);
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      setDocs(await listDocuments());
    } catch (err) {
      setError(err instanceof ApiError ? messageForCode(err.code, err.message) : 'Failed to load documents.');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  async function handleSubmit(e) {
    e.preventDefault();
    setError('');
    if (!file && !text.trim()) {
      setError('Provide some text or a file.');
      return;
    }
    setBusy(true);
    try {
      const res = await submitDocument({ file, text: text.trim() || undefined });
      // Jump to the review page whether extraction succeeded or failed;
      // the review page offers the retry action for failures.
      navigate(`/documents/${res.documentId}`);
    } catch (err) {
      if (err instanceof ApiError && err.code === 400 && err.data) {
        setError(formatValidationErrors(err.data));
      } else if (err instanceof ApiError) {
        setError(err instanceof ApiError ? messageForCode(err.code, err.message) : 'Submit failed.');
      }
    } finally {
      setBusy(false);
    }
  }

  return (
    <div>
      <h1 className="text-2xl font-bold text-slate-900">Documents</h1>
      <p className="mt-1 text-sm text-slate-500">
        Paste text or upload a PDF, PNG, JPG or WEBP file. AI extracts task drafts for you to review.
      </p>

      <ErrorBanner message={error} onDismiss={() => setError('')} />

      <form onSubmit={handleSubmit} className="mt-4 rounded-xl border border-slate-200 bg-white p-5">
        <label className="block text-sm font-medium text-slate-700">
          Text (up to 20000 characters)
          <textarea
            value={text}
            onChange={(e) => setText(e.target.value)}
            rows={5}
            maxLength={20000}
            placeholder="Paste a notice, email or any material that contains tasks..."
            className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none"
          />
        </label>

        <label className="mt-4 block text-sm font-medium text-slate-700">
          Or a file (PDF, PNG, JPG, WEBP, up to 10MB)
          <input
            type="file"
            accept={ACCEPT}
            onChange={(e) => setFile(e.target.files[0] || null)}
            className="mt-1 block w-full text-sm text-slate-600 file:mr-3 file:rounded-lg file:border-0 file:bg-brand-50 file:px-3 file:py-1.5 file:text-sm file:font-medium file:text-brand-700 hover:file:bg-brand-100"
          />
        </label>
        {file && <p className="mt-2 text-xs text-slate-400">Selected: {file.name}</p>}

        <button
          type="submit"
          disabled={busy}
          className="mt-4 rounded-lg bg-brand-600 px-4 py-2 text-sm text-white hover:bg-brand-700 disabled:opacity-50"
        >
          {busy ? 'Analyzing... this can take a minute' : 'Analyze'}
        </button>
      </form>

      <h2 className="mt-8 text-lg font-semibold text-slate-900">History</h2>
      {loading ? (
        <Spinner />
      ) : docs.length === 0 ? (
        <div className="mt-3">
          <EmptyState title="No documents yet" hint="Submit your first material above." />
        </div>
      ) : (
        <div className="mt-3 space-y-2">
          {docs.map((doc) => (
            <Link
              key={doc.id}
              to={`/documents/${doc.id}`}
              className="flex items-center justify-between rounded-lg border border-slate-200 bg-white px-4 py-3 transition hover:border-brand-500"
            >
              <div className="flex items-center gap-3">
                <StatusBadge value={doc.docType} />
                <div>
                  <p className="text-sm font-medium text-slate-900">
                    {doc.filename || 'Pasted text'}
                  </p>
                  <p className="text-xs text-slate-400">{formatInstant(doc.createdAt)}</p>
                </div>
              </div>
              <StatusBadge value={doc.status} />
            </Link>
          ))}
        </div>
      )}
    </div>
  );
}
