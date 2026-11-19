import StatusBadge from './StatusBadge';

// Editable draft card produced by AI extraction. Controlled component:
// `onChange` receives the updated candidate object.
export default function CandidateEditor({ candidate, selected, onToggle, onChange }) {
  function update(field, value) {
    onChange({ ...candidate, [field]: value });
  }

  const confidencePct = Math.round((candidate.confidence ?? 0) * 100);

  return (
    <div className="rounded-xl border border-slate-200 bg-white p-4">
      <div className="flex items-start justify-between gap-3">
        <label className="flex items-center gap-2 text-sm font-medium text-slate-700">
          <input
            type="checkbox"
            checked={selected}
            onChange={onToggle}
            className="h-4 w-4 rounded border-slate-300 text-brand-600"
          />
          Include in confirm
        </label>
        <div className="flex items-center gap-2">
          <StatusBadge value={candidate.status} />
          <span className="text-xs text-slate-400">Confidence {confidencePct}%</span>
        </div>
      </div>

      <div className="mt-1 h-1 w-full overflow-hidden rounded-full bg-slate-100">
        <div
          className={`h-full rounded-full ${confidencePct >= 70 ? 'bg-emerald-500' : 'bg-amber-500'}`}
          style={{ width: `${confidencePct}%` }}
        />
      </div>

      <div className="mt-3 space-y-3">
        <label className="block text-sm font-medium text-slate-700">
          Title
          <input
            type="text"
            value={candidate.title || ''}
            onChange={(e) => update('title', e.target.value)}
            maxLength={200}
            className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none"
          />
        </label>

        <div className="grid grid-cols-2 gap-3">
          <label className="block text-sm font-medium text-slate-700">
            Due date (required)
            <input
              type="date"
              value={candidate.dueDate || ''}
              onChange={(e) => update('dueDate', e.target.value)}
              className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none"
            />
          </label>
          <label className="block text-sm font-medium text-slate-700">
            Due time
            <input
              type="time"
              value={candidate.dueTime ? candidate.dueTime.slice(0, 5) : ''}
              onChange={(e) => update('dueTime', e.target.value || null)}
              className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none"
            />
          </label>
        </div>

        <label className="block text-sm font-medium text-slate-700">
          Place
          <input
            type="text"
            value={candidate.place || ''}
            onChange={(e) => update('place', e.target.value)}
            maxLength={200}
            className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none"
          />
        </label>

        <label className="block text-sm font-medium text-slate-700">
          Documents to bring (one per line)
          <textarea
            value={(candidate.documentsToBring || []).join('\n')}
            onChange={(e) =>
              update(
                'documentsToBring',
                e.target.value.split('\n').map((s) => s.trim()).filter(Boolean)
              )
            }
            rows={2}
            className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none"
          />
        </label>

        <label className="block text-sm font-medium text-slate-700">
          Notes
          <textarea
            value={candidate.notes || ''}
            onChange={(e) => update('notes', e.target.value)}
            rows={2}
            className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none"
          />
        </label>
      </div>
    </div>
  );
}
