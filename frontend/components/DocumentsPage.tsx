"use client";

import { useCallback, useEffect, useState } from "react";
import { useAuth } from "@/components/Auth";
import { buttonClass, Card, Field, inputClass, Message, PageTitle } from "@/components/ui";
import { deleteDocument, documents as listDocuments, uploadDocument, type DocumentInfo } from "@/lib/api";
import { backendLang, corpusLanguageName, dictionaries, locales, type Locale } from "@/lib/i18n";

export default function DocumentsPage({ locale }: { locale: Locale }) {
  const t = dictionaries[locale];
  const { user, ready } = useAuth();
  const admin = user?.role === "ADMIN";
  const [docs, setDocs] = useState<DocumentInfo[]>([]);

  const refresh = useCallback(() => {
    listDocuments().then(setDocs).catch(() => setDocs([]));
  }, []);

  // Reload when the user changes: what is visible depends on who is signed in.
  useEffect(() => {
    if (ready) refresh();
  }, [ready, user, refresh]);

  const when = new Intl.DateTimeFormat(locale, { dateStyle: "medium" });

  return (
    <div className="flex flex-col gap-6">
      <PageTitle title={t.docs.title} intro={t.docs.intro} />

      {admin && <UploadForm locale={locale} onUploaded={refresh} />}

      <ul className="flex flex-col divide-y divide-line rounded-xl border border-line bg-surface">
        {docs.map((doc) => (
          <li key={doc.id} className="flex flex-wrap items-center gap-x-4 gap-y-1 px-5 py-3 text-sm">
            <div className="min-w-0 flex-1">
              <p className="font-medium">{doc.title}</p>
              <p className="text-xs text-muted">
                {doc.kind === "eu-law" ? t.docs.euLaw : doc.filename}
                {doc.lang && ` · ${corpusLanguageName(locale, doc.lang)}`}
                {doc.kind === "upload" && ` · ${when.format(new Date(doc.createdAt))}`}
                {` · ${t.docs.units(doc.units)}`}
              </p>
            </div>
            <span
              className={`rounded-full px-2.5 py-0.5 text-xs font-medium ${
                doc.visibility === "PUBLIC" ? "bg-line text-muted" : "bg-accent-soft text-accent"
              }`}
            >
              {doc.visibility === "PUBLIC" ? t.docs.public : doc.groups.join(", ") || t.docs.restricted}
            </span>
            {admin && doc.kind === "upload" && <DeleteButton locale={locale} id={doc.id} onDeleted={refresh} />}
          </li>
        ))}
      </ul>
    </div>
  );
}

function UploadForm({ locale, onUploaded }: { locale: Locale; onUploaded: () => void }) {
  const t = dictionaries[locale];
  const [visibility, setVisibility] = useState<"PUBLIC" | "RESTRICTED">("RESTRICTED");
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<{ tone: "ok" | "error"; text: string } | null>(null);

  return (
    <Card>
      <h2 className="font-semibold">{t.docs.upload}</h2>
      <form
        className="mt-4 grid gap-4 sm:grid-cols-2"
        onSubmit={async (event) => {
          event.preventDefault();
          const form = event.currentTarget;
          setBusy(true);
          setMessage(null);
          try {
            const doc = await uploadDocument(new FormData(form));
            setMessage({ tone: "ok", text: t.docs.uploaded(doc.title, doc.units) });
            form.reset();
            onUploaded();
          } catch {
            setMessage({ tone: "error", text: t.error });
          } finally {
            setBusy(false);
          }
        }}
      >
        <div className="sm:col-span-2">
          <Field label={t.docs.file}>
            <input
              name="file"
              type="file"
              required
              accept=".pdf,.docx,.doc,.odt,.txt,.md,.html,.rtf"
              disabled={busy}
              className={`${inputClass} file:mr-3 file:rounded-md file:border-0 file:bg-accent-soft file:px-3 file:py-1 file:text-accent`}
            />
          </Field>
        </div>
        <Field label={t.docs.docTitle}>
          <input name="title" disabled={busy} className={inputClass} />
        </Field>
        <Field label={t.docs.language}>
          <select name="lang" defaultValue={backendLang[locale]} disabled={busy} className={inputClass}>
            {locales.map((l) => (
              <option key={l} value={backendLang[l]}>
                {corpusLanguageName(locale, backendLang[l])}
              </option>
            ))}
          </select>
        </Field>
        <Field label={t.docs.visibility}>
          <select
            name="visibility"
            value={visibility}
            onChange={(e) => setVisibility(e.target.value as "PUBLIC" | "RESTRICTED")}
            disabled={busy}
            className={inputClass}
          >
            <option value="RESTRICTED">{t.docs.restricted}</option>
            <option value="PUBLIC">{t.docs.public}</option>
          </select>
        </Field>
        {visibility === "RESTRICTED" && (
          <Field label={t.docs.groups} hint={t.docs.groupsHint}>
            <input name="groups" disabled={busy} className={inputClass} />
          </Field>
        )}
        <div className="flex flex-wrap items-center gap-4 sm:col-span-2">
          <button type="submit" disabled={busy} className={buttonClass}>
            {t.docs.upload}
          </button>
          {busy && <span className="animate-pulse text-sm text-muted">{t.docs.uploading}</span>}
          {message && <Message tone={message.tone}>{message.text}</Message>}
        </div>
      </form>
    </Card>
  );
}

/** Two clicks to delete: no browser dialog. */
function DeleteButton({ locale, id, onDeleted }: { locale: Locale; id: string; onDeleted: () => void }) {
  const t = dictionaries[locale];
  const [armed, setArmed] = useState(false);
  return (
    <button
      type="button"
      onClick={async () => {
        if (!armed) {
          setArmed(true);
          return;
        }
        await deleteDocument(id);
        onDeleted();
      }}
      onBlur={() => setArmed(false)}
      className={`rounded-md px-3 py-1 text-xs font-medium ${armed ? "bg-warn text-white" : "text-warn hover:bg-warn-soft"}`}
    >
      {armed ? t.docs.confirmDelete : t.docs.delete}
    </button>
  );
}
