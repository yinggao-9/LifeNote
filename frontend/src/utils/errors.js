// Maps backend business error codes to readable messages.
// Backend sends business errors as HTTP 200 with a non-200 envelope code.

const CODE_MESSAGES = {
  1001: 'Invalid email or password.',
  1002: 'Account no longer exists.',
  1003: 'User settings not found.',
  1004: 'Too many attempts. Try again later.',
  1005: 'An unexpected error occurred.',
  1006: 'This email is already registered.',
  1007: 'Invalid time zone. Use a standard zone such as Asia/Shanghai.',
  1008: 'That AI model is not available.',
  1101: 'Document not found.',
  1102: 'This item is no longer in a state that allows this action.',
  1103: 'The AI service is unavailable right now.',
  1104: 'Unsupported file type. Use PDF, PNG, JPG or WEBP.',
  1105: 'File exceeds the 10MB limit.',
  1106: 'Draft item not found.',
  1107: 'Every item needs a title and a due date.',
  1108: 'Nothing to confirm.',
  1109: 'Please check your input.',
  1201: 'Task not found.',
  1202: 'The new date must be in the future and different from the current one.',
  1203: 'Unknown status.',
  1204: 'This PDF has no text layer and could not be read.',
  1205: 'The AI returned an unexpected result.',
  1301: 'Reminder not found.',
};

export function messageForCode(code, fallback) {
  return CODE_MESSAGES[code] || fallback || 'Something went wrong.';
}

// Validation failures come back as code 400 with data = { field: message }.
export function formatValidationErrors(fieldErrors) {
  if (!fieldErrors || typeof fieldErrors !== 'object') return 'Validation failed.';
  return Object.values(fieldErrors).join(' ');
}
