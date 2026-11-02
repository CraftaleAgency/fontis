"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "@/components/Auth";
import { buttonClass, Card, Field, inputClass, Message, PageTitle } from "@/components/ui";
import { dictionaries, type Locale } from "@/lib/i18n";

export default function LoginForm({ locale }: { locale: Locale }) {
  const t = dictionaries[locale];
  const { login } = useAuth();
  const router = useRouter();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [failed, setFailed] = useState(false);
  const [busy, setBusy] = useState(false);

  return (
    <div className="mx-auto flex max-w-sm flex-col gap-6">
      <PageTitle title={t.auth.signIn} />
      <Card>
        <form
          className="flex flex-col gap-4"
          onSubmit={async (event) => {
            event.preventDefault();
            setBusy(true);
            setFailed(false);
            try {
              await login(email, password);
              router.push(`/${locale}`);
            } catch {
              setFailed(true);
            } finally {
              setBusy(false);
            }
          }}
        >
          <Field label={t.auth.email}>
            <input
              type="email"
              autoComplete="username"
              required
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              className={inputClass}
            />
          </Field>
          <Field label={t.auth.password}>
            <input
              type="password"
              autoComplete="current-password"
              required
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              className={inputClass}
            />
          </Field>
          {failed && <Message tone="error">{t.auth.wrong}</Message>}
          <button type="submit" disabled={busy} className={buttonClass}>
            {t.auth.signIn}
          </button>
        </form>
      </Card>
    </div>
  );
}
