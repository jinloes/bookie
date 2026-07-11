// @ts-check
// Shared transport layer for the generated OpenAPI client (frontend/src/generated/api).
//
// The generated *ControllerApi classes handle URL/method/param construction and are kept in
// sync with the backend via `npm run generate:api` (see frontend/openapitools.json + backend
// @Operation(operationId=...) annotations) — this is the single source of truth for what
// requests look like, so a backend/frontend contract drift (e.g. a field rename) fails at
// generation/typecheck time instead of silently dropping data at runtime.
//
// This module wraps that generated client with the app-specific behavior the generated code
// doesn't provide out of the box: resolving the Tauri backend URL, injecting a correlation ID
// header, and normalizing non-2xx responses into the existing ApiError shape the rest of the
// app already expects (notifications, 401 Outlook-reauth redirect, etc).
import { invoke } from '@tauri-apps/api/core';
import * as apis from '../generated/api/apis/index';
import { Configuration } from '../generated/api/runtime';

export function generateRequestId() {
  // Matches the UUID format the backend generates in RequestCorrelationFilter
  // when a client doesn't supply one, keeping request IDs consistent for log correlation.
  return crypto.randomUUID();
}

export class ApiError extends Error {
  constructor(status, code, message, details) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
    this.details = details || {};
    this.requestId = details?.requestId;
  }
}

function parseErrorPayload(contentType, bodyText) {
  if (!bodyText) {
    return { code: null, message: null, details: {} };
  }
  if (contentType?.includes('application/json')) {
    try {
      const parsed = JSON.parse(bodyText);
      return {
        code: parsed?.code ?? null,
        message: parsed?.message ?? parsed?.error ?? null,
        details: parsed?.details ?? {},
      };
    } catch {
      return { code: null, message: null, details: {} };
    }
  }
  return { code: null, message: bodyText, details: {} };
}

// In dev, Vite proxies /api to localhost:48763, so requests can stay relative (the generated
// client's paths already include the /api prefix from @RequestMapping). In a Tauri production
// build the frontend is served from an internal origin, so requests need the full backend URL.
let originPromise;

async function resolveOrigin() {
  if (import.meta.env.DEV) {
    return '';
  }
  try {
    return await invoke('get_backend_url');
  } catch {
    // Fallback if the Tauri command fails (e.g. running the built bundle outside Tauri).
    return 'http://localhost:48763';
  }
}

function getOrigin() {
  if (!originPromise) {
    originPromise = resolveOrigin();
  }
  return originPromise;
}

/** Custom fetch: resolves the dynamic backend origin fresh on every call and prepends it. */
async function fetchApi(url, init) {
  const origin = await getOrigin();
  return fetch(`${origin}${url}`, init);
}

const configuration = new Configuration({
  basePath: '',
  fetchApi,
  middleware: [
    {
      async pre(context) {
        context.init.headers = {
          ...context.init.headers,
          'X-Request-Id': generateRequestId(),
        };
        return context;
      },
      async post(context) {
        if (context.response.ok) {
          return context.response;
        }
        const contentType = context.response.headers.get('content-type') || '';
        let bodyText = '';
        try {
          bodyText = await context.response.clone().text();
        } catch {
          bodyText = '';
        }
        const payload = parseErrorPayload(contentType, bodyText);
        const message =
          payload.message || `HTTP ${context.response.status}: ${bodyText || 'no body'}`;
        const error = new ApiError(context.response.status, payload.code, message, payload.details);
        if (context.response.status === 401 && error.code === 'OUTLOOK_AUTH_REQUIRED') {
          // Replace history so back-button doesn't bounce the user back into a 401 loop.
          window.location.replace('/api/outlook/connect');
        }
        console.error(
          'API error',
          context.response.url,
          message,
          payload.code,
          `(requestId: ${context.init?.headers?.['X-Request-Id']})`
        );
        throw error;
      },
    },
  ],
});

export const expenseApi = new apis.ExpenseControllerApi(configuration);
export const incomeApi = new apis.IncomeControllerApi(configuration);
export const propertyApi = new apis.PropertyControllerApi(configuration);
export const payerApi = new apis.PayerControllerApi(configuration);
export const pendingExpenseApi = new apis.PendingExpenseControllerApi(configuration);
export const receiptApi = new apis.ReceiptControllerApi(configuration);
export const outlookApi = new apis.OutlookControllerApi(configuration);
export const agentApi = new apis.AgentControllerApi(configuration);
export const backupApi = new apis.BackupControllerApi(configuration);

// The generated client mis-codegens the Venmo CSV import endpoint: it mixes a multipart file
// param with query params in a way the generator doesn't detect as multipart, so it would
// serialize the file as JSON instead of building real multipart/form-data. Rather than ship a
// generated call that silently corrupts Venmo imports, this one endpoint keeps a thin manual
// fetch that reuses the same origin/request-id/error-normalization behavior as the generated
// client's middleware.
export async function rawMultipartRequest(path, formData) {
  const origin = await getOrigin();
  const requestId = generateRequestId();
  const res = await fetch(`${origin}${path}`, {
    method: 'POST',
    headers: { 'X-Request-Id': requestId },
    body: formData,
  });
  if (!res.ok) {
    const contentType = res.headers.get('content-type') || '';
    let bodyText = '';
    try {
      bodyText = await res.text();
    } catch {
      bodyText = '';
    }
    const payload = parseErrorPayload(contentType, bodyText);
    const message = payload.message || `HTTP ${res.status}: ${bodyText || 'no body'}`;
    console.error('API error', res.url, message, payload.code, `(requestId: ${requestId})`);
    throw new ApiError(res.status, payload.code, message, payload.details);
  }
  if (res.status === 204) return null;
  return res.json();
}
