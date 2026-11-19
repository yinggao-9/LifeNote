import { useEffect, useState } from 'react';
import { NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { listReminders } from '../api/reminders';

const NAV_ITEMS = [
  { to: '/', label: 'Board', end: true },
  { to: '/tasks', label: 'Tasks' },
  { to: '/documents', label: 'Documents' },
  { to: '/reminders', label: 'Reminders' },
  { to: '/summary', label: 'Summary' },
  { to: '/postponements', label: 'Postponements' },
  { to: '/settings', label: 'Settings' },
];

// App shell: sidebar navigation, top bar with unread count and logout.
export default function Layout() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [unread, setUnread] = useState(0);

  useEffect(() => {
    let cancelled = false;
    listReminders(true)
      .then((rows) => {
        if (!cancelled) setUnread(rows.length);
      })
      .catch(() => {});
    return () => {
      cancelled = true;
    };
  }, [location.pathname]);

  async function handleLogout() {
    await logout();
    navigate('/login');
  }

  return (
    <div className="flex h-full">
      <aside className="flex w-56 shrink-0 flex-col border-r border-slate-200 bg-white">
        <div className="px-5 py-6">
          <h1 className="text-xl font-bold text-brand-600">LifeNote</h1>
          <p className="mt-1 text-xs text-slate-400">Turn material into tasks</p>
        </div>
        <nav className="flex-1 space-y-1 px-3">
          {NAV_ITEMS.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.end}
              className={({ isActive }) =>
                `block rounded-lg px-3 py-2 text-sm font-medium transition ${
                  isActive
                    ? 'bg-brand-50 text-brand-700'
                    : 'text-slate-600 hover:bg-slate-100'
                }`
              }
            >
              <span className="flex items-center justify-between">
                {item.label}
                {item.to === '/reminders' && unread > 0 && (
                  <span className="rounded-full bg-red-500 px-1.5 py-0.5 text-xs text-white">
                    {unread}
                  </span>
                )}
              </span>
            </NavLink>
          ))}
        </nav>
      </aside>

      <div className="flex min-w-0 flex-1 flex-col">
        <header className="flex items-center justify-end gap-4 border-b border-slate-200 bg-white px-6 py-3">
          <span className="text-sm text-slate-600">{user?.username}</span>
          <button
            type="button"
            onClick={handleLogout}
            className="rounded-lg border border-slate-300 px-3 py-1.5 text-sm text-slate-700 hover:bg-slate-50"
          >
            Sign out
          </button>
        </header>
        <main className="flex-1 overflow-y-auto p-6">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
