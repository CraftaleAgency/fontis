// Small shared form and layout pieces.

export function PageTitle({ title, intro }: { title: string; intro?: string }) {
  return (
    <div>
      <h1 className="text-2xl font-semibold tracking-tight">{title}</h1>
      {intro && <p className="mt-2 max-w-2xl text-sm leading-relaxed text-muted">{intro}</p>}
    </div>
  );
}

export function Field({ label, hint, children }: { label: string; hint?: string; children: React.ReactNode }) {
  return (
    <label className="flex flex-col gap-1 text-sm">
      <span className="font-medium">{label}</span>
      {children}
      {hint && <span className="text-xs text-muted">{hint}</span>}
    </label>
  );
}

export const inputClass =
  "rounded-lg border border-line bg-surface px-3 py-2 text-sm outline-none focus:border-accent disabled:opacity-60";

export const buttonClass =
  "rounded-lg bg-accent px-4 py-2 text-sm font-medium text-white transition-opacity disabled:opacity-40 dark:text-[#0f1115]";

export function Message({ tone, children }: { tone: "ok" | "error"; children: React.ReactNode }) {
  return (
    <p role="status" className={`text-sm ${tone === "ok" ? "text-accent" : "text-warn"}`}>
      {children}
    </p>
  );
}

export function Card({ children }: { children: React.ReactNode }) {
  return <div className="rounded-xl border border-line bg-surface p-5">{children}</div>;
}
