import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { changePassword, deleteAccount, fetchProfile, updateUsername } from '../api/users';
import { getSettings, updateSettings } from '../api/settings';
import { ApiError } from '../api/client';
import { messageForCode } from '../utils/errors';
import { fullTime } from '../utils/format';
import { useAuth } from '../context/AuthContext';
import ConfirmDialog from '../components/ConfirmDialog';
import ErrorBanner from '../components/ErrorBanner';

// Browser-provided IANA zone list, with a small fallback.
function timeZoneOptions() {
  try {
    return Intl.supportedValuesOf('timeZone');
  } catch {
    return ['Europe/Dublin', 'Asia/Shanghai', 'UTC', 'America/New_York', 'Europe/London'];
  }
}

// Settings page: profile, preferences, password and account deletion.
// Note the backend quirks: username and password changes use query
// parameters, and the stored aiModel default 'default' cannot be saved
// back - only 'gpt' or 'deepseek' are accepted.
export default function Settings() {
  const navigate = useNavigate();
  const { setUser, forceLogout } = useAuth();

  const [email, setEmail] = useState('');
  const [username, setUsername] = useState('');
  const [profileMsg, setProfileMsg] = useState('');

  const [timeZone, setTimeZone] = useState('Europe/Dublin');
  const [eveningTime, setEveningTime] = useState('21:00');
  const [emailEnabled, setEmailEnabled] = useState(false);
  const [aiModel, setAiModel] = useState('gpt');
  const [settingsMsg, setSettingsMsg] = useState('');

  const [newPassword, setNewPassword] = useState('');
  const [passwordMsg, setPasswordMsg] = useState('');

  const [deletePassword, setDeletePassword] = useState('');
  const [deleteOpen, setDeleteOpen] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    async function load() {
      setError('');
      try {
        const [profile, settings] = await Promise.all([fetchProfile(), getSettings()]);
        setEmail(profile.email);
        setUsername(profile.username);
        setTimeZone(settings.timeZone || 'Europe/Dublin');
        setEveningTime(settings.eveningReminderTime ? settings.eveningReminderTime.slice(0, 5) : '21:00');
        setEmailEnabled(Boolean(settings.emailRemindersEnabled));
        // 'default' is the stored placeholder and is rejected on update;
        // fall back to a valid choice.
        setAiModel(settings.aiModel === 'deepseek' ? 'deepseek' : 'gpt');
      } catch (err) {
        setError(err instanceof ApiError ? messageForCode(err.code, err.message) : 'Failed to load settings.');
      }
    }
    load();
  }, []);

  async function saveProfile(e) {
    e.preventDefault();
    setProfileMsg('');
    setError('');
    setBusy(true);
    try {
      const user = await updateUsername(username.trim());
      setUser({ userId: user.id, email: user.email, username: user.username });
      setProfileMsg('Profile updated.');
    } catch (err) {
      setError(err instanceof ApiError ? messageForCode(err.code, err.message) : 'Update failed.');
    } finally {
      setBusy(false);
    }
  }

  async function saveSettings(e) {
    e.preventDefault();
    setSettingsMsg('');
    setError('');
    setBusy(true);
    try {
      await updateSettings({
        timeZone,
        // Backend expects 'HH:mm:ss' exactly.
        eveningReminderTime: fullTime(eveningTime),
        emailRemindersEnabled: emailEnabled,
        aiModel,
      });
      setSettingsMsg('Settings saved.');
    } catch (err) {
      setError(err instanceof ApiError ? messageForCode(err.code, err.message) : 'Save failed.');
    } finally {
      setBusy(false);
    }
  }

  async function savePassword(e) {
    e.preventDefault();
    setPasswordMsg('');
    setError('');
    if (newPassword.length < 8) {
      setError('Password must be at least 8 characters.');
      return;
    }
    setBusy(true);
    try {
      await changePassword(newPassword);
      setNewPassword('');
      setPasswordMsg('Password changed.');
    } catch (err) {
      setError(err instanceof ApiError ? messageForCode(err.code, err.message) : 'Change failed.');
    } finally {
      setBusy(false);
    }
  }

  async function handleDelete() {
    setBusy(true);
    setError('');
    try {
      await deleteAccount(deletePassword);
      forceLogout();
      navigate('/login');
    } catch (err) {
      setError(err instanceof ApiError ? messageForCode(err.code, err.message) : 'Delete failed.');
      setDeleteOpen(false);
      setBusy(false);
    }
  }

  return (
    <div className="mx-auto max-w-3xl">
      <h1 className="text-2xl font-bold text-slate-900">Settings</h1>
      <div className="mt-4">
        <ErrorBanner message={error} onDismiss={() => setError('')} />
      </div>

      {/* Profile */}
      <form onSubmit={saveProfile} className="mt-6 rounded-xl border border-slate-200 bg-white p-5">
        <h2 className="text-sm font-semibold text-slate-700">Profile</h2>
        <label className="mt-3 block text-sm font-medium text-slate-700">
          Email
          <input
            type="email"
            value={email}
            readOnly
            disabled
            className="mt-1 w-full rounded-lg border border-slate-200 bg-slate-50 px-3 py-2 text-sm text-slate-500"
          />
        </label>
        <label className="mt-3 block text-sm font-medium text-slate-700">
          Username (2-30 characters)
          <input
            type="text"
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            required
            minLength={2}
            maxLength={30}
            className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none"
          />
        </label>
        <div className="mt-4 flex items-center gap-3">
          <button
            type="submit"
            disabled={busy}
            className="rounded-lg bg-brand-600 px-4 py-2 text-sm text-white hover:bg-brand-700 disabled:opacity-50"
          >
            Save profile
          </button>
          {profileMsg && <span className="text-sm text-emerald-600">{profileMsg}</span>}
        </div>
      </form>

      {/* Preferences */}
      <form onSubmit={saveSettings} className="mt-6 rounded-xl border border-slate-200 bg-white p-5">
        <h2 className="text-sm font-semibold text-slate-700">Preferences</h2>

        <label className="mt-3 block text-sm font-medium text-slate-700">
          Time zone
          <input
            type="text"
            list="timezones"
            value={timeZone}
            onChange={(e) => setTimeZone(e.target.value)}
            required
            placeholder="e.g. Asia/Shanghai"
            className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none"
          />
          <datalist id="timezones">
            {timeZoneOptions().map((tz) => (
              <option key={tz} value={tz} />
            ))}
          </datalist>
        </label>

        <label className="mt-3 block text-sm font-medium text-slate-700">
          Evening reminder time
          <input
            type="time"
            value={eveningTime}
            onChange={(e) => setEveningTime(e.target.value)}
            required
            className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none"
          />
        </label>

        <label className="mt-3 flex items-center gap-2 text-sm font-medium text-slate-700">
          <input
            type="checkbox"
            checked={emailEnabled}
            onChange={(e) => setEmailEnabled(e.target.checked)}
            className="h-4 w-4 rounded border-slate-300 text-brand-600"
          />
          Also send reminders by email
        </label>

        <label className="mt-3 block text-sm font-medium text-slate-700">
          AI model
          <select
            value={aiModel}
            onChange={(e) => setAiModel(e.target.value)}
            className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none"
          >
            <option value="gpt">GPT</option>
            <option value="deepseek">DeepSeek</option>
          </select>
        </label>

        <div className="mt-4 flex items-center gap-3">
          <button
            type="submit"
            disabled={busy}
            className="rounded-lg bg-brand-600 px-4 py-2 text-sm text-white hover:bg-brand-700 disabled:opacity-50"
          >
            Save settings
          </button>
          {settingsMsg && <span className="text-sm text-emerald-600">{settingsMsg}</span>}
        </div>
      </form>

      {/* Password */}
      <form onSubmit={savePassword} className="mt-6 rounded-xl border border-slate-200 bg-white p-5">
        <h2 className="text-sm font-semibold text-slate-700">Change password</h2>
        <label className="mt-3 block text-sm font-medium text-slate-700">
          New password (at least 8 characters)
          <input
            type="password"
            value={newPassword}
            onChange={(e) => setNewPassword(e.target.value)}
            required
            minLength={8}
            className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none"
          />
        </label>
        <div className="mt-4 flex items-center gap-3">
          <button
            type="submit"
            disabled={busy}
            className="rounded-lg bg-brand-600 px-4 py-2 text-sm text-white hover:bg-brand-700 disabled:opacity-50"
          >
            Change password
          </button>
          {passwordMsg && <span className="text-sm text-emerald-600">{passwordMsg}</span>}
        </div>
      </form>

      {/* Danger zone */}
      <div className="mt-6 rounded-xl border border-red-200 bg-white p-5">
        <h2 className="text-sm font-semibold text-red-700">Delete account</h2>
        <p className="mt-1 text-sm text-slate-500">
          This removes your account, all tasks, documents and settings. It cannot be undone.
        </p>
        <div className="mt-3 flex flex-wrap items-center gap-3">
          <input
            type="password"
            value={deletePassword}
            onChange={(e) => setDeletePassword(e.target.value)}
            placeholder="Confirm with your password"
            className="rounded-lg border border-slate-300 px-3 py-2 text-sm focus:border-red-500 focus:outline-none"
          />
          <button
            type="button"
            onClick={() => setDeleteOpen(true)}
            disabled={!deletePassword}
            className="rounded-lg bg-red-600 px-4 py-2 text-sm text-white hover:bg-red-700 disabled:opacity-50"
          >
            Delete my account
          </button>
        </div>
      </div>

      <ConfirmDialog
        open={deleteOpen}
        title="Delete your account?"
        message="All data will be permanently removed. This action cannot be undone."
        confirmLabel="Delete forever"
        danger
        busy={busy}
        onConfirm={handleDelete}
        onCancel={() => setDeleteOpen(false)}
      />
    </div>
  );
}
