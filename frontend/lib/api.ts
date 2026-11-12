// Types of the Fontis backend API (see docs/answering.md). Requests go through the /api rewrite in
// next.config.ts, so the browser never calls the backend directly.

export type Status = "ANSWERED" | "NO_SOURCE" | "NO_ANSWER" | "UNCITED";

export type Citation = {
  n: number;
  celex: string;
  unitId: string;
  document: string;
  label: string;
  heading: string | null;
  url: string;
  matchedLang: string;
};

export type AskResponse = {
  question: string;
  status: Status;
  answer: string | null;
  citations: Citation[];
  sources: Citation[];
  model: string | null;
  retrievalMs: number;
  generationMs: number;
};

export type LogEntry = {
  id: number;
  createdAt: string;
  question: string;
  lang: string;
  status: Status;
  answer: string | null;
  citations: Citation[];
  model: string | null;
  retrievalMs: number;
  generationMs: number;
};

export async function ask(question: string, lang: string): Promise<AskResponse> {
  return json(
    await send("/api/ask", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ question, lang }),
    }),
    "Ask",
  );
}

export async function recentAnswers(limit = 50): Promise<LogEntry[]> {
  return json(await fetch(`/api/log?limit=${limit}`, { cache: "no-store" }), "Log");
}

/**
 * Splits an answer into text and citation numbers, dropping the space before a marker. Besides [n],
 * accepts the lists and ranges some models write: [1, 3], [1-3], [1–3].
 */
export function splitCitations(answer: string): (string | number)[] {
  const parts = answer.split(/\[(\d{1,3}(?:\s*[,;–-]\s*\d{1,3})*)\]/);
  return parts.flatMap<string | number>((part, i) => {
    if (i % 2 === 1) return citationNumbers(part);
    return [i + 1 < parts.length ? part.replace(/\s+$/, "") : part];
  });
}

function citationNumbers(marker: string): number[] {
  const numbers: number[] = [];
  for (const [, from, to] of marker.matchAll(/(\d{1,3})(?:\s*[–-]\s*(\d{1,3}))?/g)) {
    const last = Math.min(Number(to ?? from), Number(from) + 20);
    for (let n = Number(from); n <= last; n++) numbers.push(n);
  }
  return numbers;
}

/** "Recital 28" instead of the bare "(28)" label the corpus uses for recitals. */
export function sourceLabel(citation: Citation, recitalWord: string): string {
  return citation.unitId.startsWith("rct_") ? `${recitalWord} ${citation.unitId.slice(4)}` : citation.label;
}

// ---------- Session and CSRF ----------
// The backend uses a session cookie; state-changing requests must echo the XSRF-TOKEN cookie
// in the X-XSRF-TOKEN header (Spring Security's SPA CSRF setup).

function csrfToken(): string {
  const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]+)/);
  return match ? decodeURIComponent(match[1]) : "";
}

async function send(url: string, init: RequestInit = {}): Promise<Response> {
  if (!csrfToken()) {
    await fetch("/api/auth/me", { cache: "no-store" });
  }
  return fetch(url, { ...init, headers: { ...init.headers, "X-XSRF-TOKEN": csrfToken() } });
}

async function json<T>(response: Response, what: string): Promise<T> {
  if (!response.ok) {
    throw new ApiError(what, response.status);
  }
  return response.json();
}

export class ApiError extends Error {
  constructor(
    what: string,
    readonly status: number,
  ) {
    super(`${what} failed: ${status}`);
  }
}

export type Role = "ADMIN" | "USER";
export type User = { id: number; email: string; displayName: string; role: Role; groups: string[] };

/** The signed-in user, or null. Also obtains the CSRF cookie. */
export async function me(): Promise<User | null> {
  const response = await fetch("/api/auth/me", { cache: "no-store" });
  return response.status === 204 ? null : json<User>(response, "Me");
}

export async function login(email: string, password: string): Promise<User> {
  return json<User>(
    await send("/api/auth/login", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ email, password }),
    }),
    "Login",
  );
}

export async function logout(): Promise<void> {
  await send("/api/auth/logout", { method: "POST" });
}

// ---------- Documents ----------

export type Visibility = "PUBLIC" | "RESTRICTED";
export type DocumentInfo = {
  id: string;
  title: string;
  kind: "eu-law" | "upload";
  visibility: Visibility;
  lang: string | null;
  filename: string | null;
  groups: string[];
  units: number;
  chunks: number;
  createdAt: string;
};

export async function documents(): Promise<DocumentInfo[]> {
  return json(await fetch("/api/documents", { cache: "no-store" }), "Documents");
}

export async function uploadDocument(form: FormData): Promise<DocumentInfo> {
  return json(await send("/api/documents", { method: "POST", body: form }), "Upload");
}

export async function deleteDocument(id: string): Promise<void> {
  const response = await send(`/api/documents/${encodeURIComponent(id)}`, { method: "DELETE" });
  if (!response.ok) throw new ApiError("Delete", response.status);
}

// ---------- Users (administrators) ----------

export async function users(): Promise<User[]> {
  return json(await fetch("/api/admin/users", { cache: "no-store" }), "Users");
}

export async function createUser(user: {
  email: string;
  displayName: string;
  password: string;
  role: Role;
  groups: string[];
}): Promise<User> {
  return json(
    await send("/api/admin/users", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(user),
    }),
    "Create user",
  );
}

export async function deleteUser(id: number): Promise<void> {
  const response = await send(`/api/admin/users/${id}`, { method: "DELETE" });
  if (!response.ok) throw new ApiError("Delete user", response.status);
}
