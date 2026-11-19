import { Link } from 'react-router-dom';
import StatusBadge from './StatusBadge';
import { shortTime, formatDateDisplay } from '../utils/format';

// Compact task card used on the board and list views.
export default function TaskCard({ task }) {
  return (
    <Link
      to={`/tasks/${task.id}`}
      className="block rounded-lg border border-slate-200 bg-white p-3 shadow-sm transition hover:border-brand-500 hover:shadow"
    >
      <div className="flex items-start justify-between gap-2">
        <p className="font-medium text-slate-900 leading-snug">{task.title}</p>
        <StatusBadge value={task.status} />
      </div>
      <div className="mt-2 flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-slate-500">
        <span>{formatDateDisplay(task.dueDate)}</span>
        {task.dueTime && <span>{shortTime(task.dueTime)}</span>}
        {task.place && <span>{task.place}</span>}
      </div>
      <div className="mt-2 flex items-center gap-2">
        <StatusBadge value={task.source} />
        {task.postponedFromTaskId && (
          <span className="text-xs text-amber-600">Postponed from earlier</span>
        )}
      </div>
    </Link>
  );
}
