// Placeholder shown when a list has no items.
export default function EmptyState({ title = 'Nothing here yet', hint }) {
  return (
    <div className="rounded-lg border border-dashed border-slate-300 bg-white px-6 py-12 text-center">
      <p className="text-slate-600 font-medium">{title}</p>
      {hint && <p className="mt-1 text-sm text-slate-400">{hint}</p>}
    </div>
  );
}
