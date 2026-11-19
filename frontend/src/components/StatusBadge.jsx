const STYLES = {
  TODO: 'bg-blue-100 text-blue-700',
  DONE: 'bg-emerald-100 text-emerald-700',
  CANCELLED: 'bg-slate-200 text-slate-600',
  POSTPONED: 'bg-amber-100 text-amber-700',
  PENDING: 'bg-amber-100 text-amber-700',
  CONFIRMED: 'bg-emerald-100 text-emerald-700',
  PENDING_CONFIRM: 'bg-blue-100 text-blue-700',
  FAILED: 'bg-red-100 text-red-700',
  DEADLINE: 'bg-red-100 text-red-700',
  EVENING: 'bg-indigo-100 text-indigo-700',
  MANUAL: 'bg-slate-100 text-slate-600',
  QUICK: 'bg-violet-100 text-violet-700',
  AI: 'bg-cyan-100 text-cyan-700',
  TEXT: 'bg-slate-100 text-slate-600',
  IMAGE: 'bg-fuchsia-100 text-fuchsia-700',
  PDF: 'bg-rose-100 text-rose-700',
};

export default function StatusBadge({ value, label }) {
  if (!value) return null;
  return (
    <span
      className={`inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium ${
        STYLES[value] || 'bg-slate-100 text-slate-600'
      }`}
    >
      {label || value.replace(/_/g, ' ')}
    </span>
  );
}
