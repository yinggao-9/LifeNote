import { useCallback, useEffect, useState } from 'react';
import {
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  Legend,
  Pie,
  PieChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import { getSummary } from '../api/summary';
import { ApiError } from '../api/client';
import { messageForCode } from '../utils/errors';
import { monthString } from '../utils/format';
import Spinner from '../components/Spinner';
import ErrorBanner from '../components/ErrorBanner';

const PIE_COLORS = {
  done: '#10b981',
  todo: '#3b82f6',
  cancelled: '#94a3b8',
  postponed: '#f59e0b',
};

// Monthly summary: rule-based counts from the backend, plus charts.
export default function Summary() {
  const [month, setMonth] = useState(monthString(new Date()));
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const load = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      setData(await getSummary(month));
    } catch (err) {
      setError(err instanceof ApiError ? messageForCode(err.code, err.message) : 'Failed to load the summary.');
    } finally {
      setLoading(false);
    }
  }, [month]);

  useEffect(() => {
    load();
  }, [load]);

  const counts = data?.counts || {};
  const pieData = [
    { name: 'Done', value: counts.done || 0, key: 'done' },
    { name: 'To do', value: counts.todo || 0, key: 'todo' },
    { name: 'Cancelled', value: counts.cancelled || 0, key: 'cancelled' },
    { name: 'Postponed', value: counts.postponed || 0, key: 'postponed' },
  ].filter((d) => d.value > 0);

  const dailyData = (data?.daily || []).map((d) => ({
    ...d,
    label: d.date.slice(8),
  }));

  return (
    <div>
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-slate-900">Monthly summary</h1>
          <p className="mt-1 text-sm text-slate-500">
            Counts are based on due dates within the month.
          </p>
        </div>
        <input
          type="month"
          value={month}
          onChange={(e) => setMonth(e.target.value)}
          className="rounded-lg border border-slate-300 px-3 py-1.5 text-sm focus:border-brand-500 focus:outline-none"
        />
      </div>

      <div className="mt-4">
        <ErrorBanner message={error} onDismiss={() => setError('')} />
      </div>

      {loading ? (
        <Spinner />
      ) : data ? (
        <>
          <div className="mt-6 grid grid-cols-2 gap-4 md:grid-cols-4 xl:grid-cols-7">
            {[
              { label: 'Total', value: counts.total },
              { label: 'Done', value: counts.done },
              { label: 'To do', value: counts.todo },
              { label: 'Cancelled', value: counts.cancelled },
              { label: 'Postponed', value: counts.postponed },
              {
                label: 'Completion',
                value: `${Math.round((data.completionRate || 0) * 100)}%`,
              },
              { label: 'Postpone actions', value: data.postponementCount },
            ].map((card) => (
              <div key={card.label} className="rounded-xl border border-slate-200 bg-white p-4">
                <p className="text-xs text-slate-400">{card.label}</p>
                <p className="mt-1 text-2xl font-semibold text-slate-900">{card.value ?? 0}</p>
              </div>
            ))}
          </div>

          {data.llmSummary && (
            <div className="mt-6 rounded-xl border border-brand-200 bg-brand-50 p-5">
              <h2 className="text-sm font-semibold text-brand-800">AI overview</h2>
              <p className="mt-2 text-sm text-slate-700">{data.llmSummary}</p>
            </div>
          )}

          <div className="mt-6 grid grid-cols-1 gap-6 lg:grid-cols-2">
            <div className="rounded-xl border border-slate-200 bg-white p-5">
              <h2 className="text-sm font-semibold text-slate-700">Daily volume</h2>
              <div className="mt-4 h-64">
                <ResponsiveContainer width="100%" height="100%">
                  <BarChart data={dailyData}>
                    <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" />
                    <XAxis dataKey="label" tick={{ fontSize: 11 }} interval={2} />
                    <YAxis allowDecimals={false} tick={{ fontSize: 11 }} />
                    <Tooltip />
                    <Legend />
                    <Bar dataKey="total" name="Total" fill="#3b82f6" radius={[3, 3, 0, 0]} />
                    <Bar dataKey="done" name="Done" fill="#10b981" radius={[3, 3, 0, 0]} />
                  </BarChart>
                </ResponsiveContainer>
              </div>
            </div>

            <div className="rounded-xl border border-slate-200 bg-white p-5">
              <h2 className="text-sm font-semibold text-slate-700">Status distribution</h2>
              <div className="mt-4 h-64">
                {pieData.length === 0 ? (
                  <p className="pt-10 text-center text-sm text-slate-400">No tasks this month.</p>
                ) : (
                  <ResponsiveContainer width="100%" height="100%">
                    <PieChart>
                      <Pie
                        data={pieData}
                        dataKey="value"
                        nameKey="name"
                        innerRadius={55}
                        outerRadius={85}
                        paddingAngle={2}
                      >
                        {pieData.map((entry) => (
                          <Cell key={entry.key} fill={PIE_COLORS[entry.key]} />
                        ))}
                      </Pie>
                      <Tooltip />
                      <Legend />
                    </PieChart>
                  </ResponsiveContainer>
                )}
              </div>
            </div>
          </div>

          <div className="mt-6 rounded-xl border border-slate-200 bg-white p-5">
            <h2 className="text-sm font-semibold text-slate-700">Most postponed items</h2>
            {data.topPostponedItems && data.topPostponedItems.length > 0 ? (
              <ul className="mt-3 divide-y divide-slate-100">
                {data.topPostponedItems.map((item) => (
                  <li key={item.itemId} className="flex items-center justify-between py-2 text-sm">
                    <span className="text-slate-700">{item.title}</span>
                    <span className="rounded-full bg-amber-100 px-2 py-0.5 text-xs text-amber-700">
                      {item.times} time(s)
                    </span>
                  </li>
                ))}
              </ul>
            ) : (
              <p className="mt-3 text-sm text-slate-400">Nothing was postponed this month.</p>
            )}
          </div>
        </>
      ) : null}
    </div>
  );
}
