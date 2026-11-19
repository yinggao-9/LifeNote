// Date/time helpers. Backend contract: dates are 'yyyy-MM-dd',
// times are 'HH:mm:ss' in responses and 'HH:mm:ss' for settings updates.

export function toDateString(date) {
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, '0');
  const d = String(date.getDate()).padStart(2, '0');
  return `${y}-${m}-${d}`;
}

export function todayString() {
  return toDateString(new Date());
}

export function parseDate(dateString) {
  if (!dateString) return null;
  const [y, m, d] = dateString.split('-').map(Number);
  return new Date(y, m - 1, d);
}

// 'HH:mm:ss' -> 'HH:mm' for display.
export function shortTime(timeString) {
  if (!timeString) return '';
  return timeString.slice(0, 5);
}

// Normalize a time input ('HH:mm' or 'HH:mm:ss') to 'HH:mm:ss'.
export function fullTime(timeString) {
  if (!timeString) return null;
  return timeString.length === 5 ? `${timeString}:00` : timeString;
}

// ISO instant -> local datetime string for display.
export function formatInstant(instant) {
  if (!instant) return '';
  const d = new Date(instant);
  return d.toLocaleString();
}

export function formatDateDisplay(dateString) {
  const d = parseDate(dateString);
  if (!d) return '';
  return d.toLocaleDateString(undefined, {
    weekday: 'short',
    year: 'numeric',
    month: 'short',
    day: 'numeric',
  });
}

export function monthString(date) {
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, '0');
  return `${y}-${m}`;
}

// Days of a month as 'yyyy-MM-dd' strings, for the summary chart.
export function daysInMonth(monthStr) {
  const [y, m] = monthStr.split('-').map(Number);
  const count = new Date(y, m, 0).getDate();
  return Array.from({ length: count }, (_, i) => {
    return `${y}-${String(m).padStart(2, '0')}-${String(i + 1).padStart(2, '0')}`;
  });
}
