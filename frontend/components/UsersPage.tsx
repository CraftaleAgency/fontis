"use client";

import { useCallback, useEffect, useState } from "react";
import { useAuth } from "@/components/Auth";
import { buttonClass, Card, Field, inputClass, Message, PageTitle } from "@/components/ui";
import { createUser, deleteUser, users as listUsers, type Role, type User } from "@/lib/api";
import { dictionaries, type Locale } from "@/lib/i18n";

export default function UsersPage({ locale }: { locale: Locale }) {
  const t = dictionaries[locale];
  const { user, ready } = useAuth();
  const [all, setAll] = useState<User[]>([]);
  const [message, setMessage] = useState<{ tone: "ok" | "error"; text: string } | null>(null);

  const refresh = useCallback(() => {
    listUsers().then(setAll).catch(() => setAll([]));
  }, []);

  useEffect(() => {
    if (user?.role === "ADMIN") refresh();
  }, [user, refresh]);

  if (!ready) return null;
  if (user?.role !== "ADMIN") {
    return <PageTitle title={t.users.title} intro={t.auth.adminOnly} />;
  }

  return (
    <div className="flex flex-col gap-6">
      <PageTitle title={t.users.title} />

      <Card>
        <h2 className="font-semibold">{t.users.create}</h2>
        <form
          className="mt-4 grid gap-4 sm:grid-cols-2"
          onSubmit={async (event) => {
            event.preventDefault();
            const form = event.currentTarget;
            const data = new FormData(form);
            setMessage(null);
            try {
              const created = await createUser({
                email: String(data.get("email")),
                displayName: String(data.get("displayName")),
                password: String(data.get("password")),
                role: data.get("role") as Role,
                groups: String(data.get("groups") ?? "").split(","),
              });
              setMessage({ tone: "ok", text: t.users.created(created.email) });
              form.reset();
              refresh();
            } catch {
              setMessage({ tone: "error", text: t.error });
            }
          }}
        >
          <Field label={t.auth.email}>
            <input name="email" type="email" required className={inputClass} />
          </Field>
          <Field label={t.users.name}>
            <input name="displayName" required className={inputClass} />
          </Field>
          <Field label={t.auth.password} hint={t.users.passwordHint}>
            <input name="password" type="password" minLength={12} required autoComplete="new-password" className={inputClass} />
          </Field>
          <Field label={t.users.role}>
            <select name="role" defaultValue="USER" className={inputClass}>
              <option value="USER">{t.users.user}</option>
              <option value="ADMIN">{t.users.admin}</option>
            </select>
          </Field>
          <Field label={t.docs.groups} hint={t.docs.groupsHint}>
            <input name="groups" className={inputClass} />
          </Field>
          <div className="flex items-end gap-4">
            <button type="submit" className={buttonClass}>
              {t.users.create}
            </button>
          </div>
          {message && (
            <div className="sm:col-span-2">
              <Message tone={message.tone}>{message.text}</Message>
            </div>
          )}
        </form>
      </Card>

      <ul className="flex flex-col divide-y divide-line rounded-xl border border-line bg-surface">
        {all.map((u) => (
          <li key={u.id} className="flex flex-wrap items-center gap-x-4 gap-y-1 px-5 py-3 text-sm">
            <div className="min-w-0 flex-1">
              <p className="font-medium">{u.displayName}</p>
              <p className="text-xs text-muted">{u.email}</p>
            </div>
            <span className="text-xs text-muted">{u.groups.join(", ")}</span>
            <span
              className={`rounded-full px-2.5 py-0.5 text-xs font-medium ${
                u.role === "ADMIN" ? "bg-accent-soft text-accent" : "bg-line text-muted"
              }`}
            >
              {u.role === "ADMIN" ? t.users.admin : t.users.user}
            </span>
            {u.id !== user.id && (
              <button
                type="button"
                onClick={async () => {
                  await deleteUser(u.id);
                  refresh();
                }}
                className="rounded-md px-3 py-1 text-xs font-medium text-warn hover:bg-warn-soft"
              >
                {t.docs.delete}
              </button>
            )}
          </li>
        ))}
      </ul>
    </div>
  );
}
