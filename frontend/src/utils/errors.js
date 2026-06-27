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
  if (error.code && ERROR_COPY[error.code]) {
    return ERROR_COPY[error.code];
  }
  if (error.message && !error.message.startsWith('HTTP ')) {
    return error.message;
  }
  return fallback;
}
