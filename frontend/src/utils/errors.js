const ERROR_COPY = {
  OUTLOOK_AUTH_REQUIRED: 'Outlook access expired. Reconnect Outlook in Settings to continue.',
  SERVICE_UNAVAILABLE: 'Service is temporarily unavailable. Try again in a moment.',
  BAD_REQUEST: 'Some values are invalid. Review the form and try again.',
  INVALID_ARGUMENT: 'Some values are invalid. Review the form and try again.',
  CONFLICT: 'This action conflicts with current data. Refresh and retry.',
  NOT_FOUND: 'The requested item no longer exists. Refresh and try again.',
  INTERNAL_ERROR: 'Something went wrong on the server. Try again.',
  IO_ERROR: 'File transfer failed. Check your connection and try again.',
};

export function getErrorMessage(error, fallback = 'Something went wrong. Please try again.') {
  if (!error) {
    return fallback;
  }
  // Prefer the backend's specific message (e.g. "This item has already been saved as
  // Expense #84") over the generic per-code copy below — the generic copy is only a
  // fallback for when the backend didn't supply a useful message.
  let message;
  if (error.message && !error.message.startsWith('HTTP ')) {
    message = error.message;
  } else if (error.code && ERROR_COPY[error.code]) {
    message = ERROR_COPY[error.code];
  } else {
    message = fallback;
  }
  // Append a short request-id reference so a user-reported error can be traced back to the
  // exact backend log entry (via RequestCorrelationFilter/MDC) without live reproduction.
  return error.requestId ? `${message} (Ref: ${error.requestId.slice(0, 8)})` : message;
}
