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
  const response = await fetch("/api/ask", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ question, lang }),
  });
  if (!response.ok) {
    throw new Error(`Ask failed: ${response.status}`);
  }
  return response.json();
}

export async function recentAnswers(limit = 50): Promise<LogEntry[]> {
  const response = await fetch(`/api/log?limit=${limit}`, { cache: "no-store" });
  if (!response.ok) {
    throw new Error(`Log failed: ${response.status}`);
  }
  return response.json();
}

/** Splits an answer into text and [n] citation markers, dropping the space before a marker. */
export function splitCitations(answer: string): (string | number)[] {
  const parts = answer.split(/\[(\d+)\]/);
  return parts.map((part, i) => {
    if (i % 2 === 1) return Number(part);
    return i + 1 < parts.length ? part.replace(/\s+$/, "") : part;
  });
}

/** "Recital 28" instead of the bare "(28)" label the corpus uses for recitals. */
export function sourceLabel(citation: Citation, recitalWord: string): string {
  return citation.unitId.startsWith("rct_") ? `${recitalWord} ${citation.unitId.slice(4)}` : citation.label;
}
