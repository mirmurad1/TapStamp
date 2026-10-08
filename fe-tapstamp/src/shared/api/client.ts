import createClient, { type Middleware } from 'openapi-fetch';

import type { paths } from './schema';

/** Backend-in SPA CSRF sxemi: token oxuna bilən cookie-dədir, dəyişdirici sorğularda başlıqda geri göndərilir. */
export const CSRF_COOKIE = '__Host-XSRF-TOKEN';
export const CSRF_HEADER = 'X-XSRF-TOKEN';

const SAFE_METHODS = new Set(['GET', 'HEAD', 'OPTIONS']);

/** RFC 7807 Problem Details cavabı (backend-in əlavə sahələri ilə). */
export interface ProblemDetail {
  type?: string;
  title?: string;
  detail?: string;
  status?: number;
  code?: string;
  traceId?: string;
  retryAfterSeconds?: number;
  attemptsRemaining?: number;
  violations?: string[];
  errors?: { field: string; code: string; message: string }[];
}

/**
 * API xətası. Mətn backend-in mesajından yox, sabit {@link code}-dan götürülür (`errors:<CODE>` açarı) —
 * beləliklə xəta həmişə istifadəçinin hazırkı dilində göstərilir.
 */
export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly problem: ProblemDetail;

  constructor(status: number, code: string, problem: ProblemDetail = {}) {
    super(code);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
    this.problem = problem;
  }
}

export function isApiError(error: unknown, code?: string): error is ApiError {
  return error instanceof ApiError && (code === undefined || error.code === code);
}

export function readCookie(name: string, cookieString: string = document.cookie): string | undefined {
  for (const part of cookieString.split(';')) {
    const [key, ...rest] = part.trim().split('=');
    if (key === name) {
      return decodeURIComponent(rest.join('='));
    }
  }
  return undefined;
}

let csrfRequest: Promise<void> | undefined;

/** CSRF cookie-si yoxdursa, bir dəfə alınır (paralel sorğular eyni sorğunu gözləyir). */
async function csrfToken(): Promise<string> {
  const existing = readCookie(CSRF_COOKIE);
  if (existing) {
    return existing;
  }
  csrfRequest ??= fetch('/api/v1/csrf', { credentials: 'same-origin' })
    .then(() => undefined)
    .finally(() => {
      csrfRequest = undefined;
    });
  await csrfRequest;
  return readCookie(CSRF_COOKIE) ?? '';
}

const csrfMiddleware: Middleware = {
  async onRequest({ request }) {
    if (!SAFE_METHODS.has(request.method.toUpperCase())) {
      request.headers.set(CSRF_HEADER, await csrfToken());
    }
    return request;
  },
};

/**
 * Müştəri tətbiqi: access token-in vaxtı keçəndə (401) sessiya bir dəfə yenilənir və sorğu təkrarlanır. Admin tətbiqi
 * bunu qoşmur (orada 401 giriş səhifəsinə aparır). Funksiya {@code true} qaytarırsa, yeni cookie-lər brauzerdədir.
 */
type SessionRefresher = () => Promise<boolean>;
let sessionRefresher: SessionRefresher | undefined;

export function setSessionRefresher(refresher: SessionRefresher | undefined): void {
  sessionRefresher = refresher;
}

/** Bu yollarda 401 təkrar yeniləməyə səbəb olmur (giriş axınının özü və işçi panelləri). */
const NO_REFRESH = /^\/api\/v1\/(auth\/(refresh|otp|register|logout|session)|admin\/|platform\/)/;

async function fetchWithRefresh(request: Request): Promise<Response> {
  const path = new URL(request.url).pathname;
  const retry = sessionRefresher && !NO_REFRESH.test(path) ? request.clone() : undefined;
  const response = await globalThis.fetch(request);
  if (response.status === 401 && retry && sessionRefresher && (await sessionRefresher())) {
    return globalThis.fetch(retry);
  }
  return response;
}

export const api = createClient<paths>({
  // Nisbi ünvan: sorğu həmişə səhifənin öz domeninə gedir (tenant domendən müəyyən olunur)
  baseUrl: typeof window === 'undefined' ? '' : window.location.origin,
  credentials: 'same-origin',
  // fetch çağırış anında götürülür (testlərdə əvəz etmək üçün)
  fetch: fetchWithRefresh,
});
api.use(csrfMiddleware);

type FetchResult<T> = { data?: T; error?: unknown; response: Response };

/** openapi-fetch nəticəsini açır: uğurlu cavabda məlumatı qaytarır, əks halda {@link ApiError} atır. */
export async function unwrap<T>(request: Promise<FetchResult<T>>): Promise<T> {
  let result: FetchResult<T>;
  try {
    result = await request;
  } catch {
    throw new ApiError(0, 'NETWORK_ERROR');
  }
  if (!result.response.ok) {
    const problem = (typeof result.error === 'object' && result.error !== null ? result.error : {}) as ProblemDetail;
    throw new ApiError(result.response.status, problem.code ?? 'UNKNOWN', problem);
  }
  return result.data as T;
}
