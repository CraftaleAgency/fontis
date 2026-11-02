"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useAuth } from "@/components/Auth";
import LanguageSwitcher from "@/components/LanguageSwitcher";
import NavLink from "@/components/NavLink";
import { dictionaries, type Locale } from "@/lib/i18n";

export default function Header({ locale }: { locale: Locale }) {
  const t = dictionaries[locale];
  const { user, ready, logout } = useAuth();
  const router = useRouter();
  const admin = user?.role === "ADMIN";

  return (
    <header className="border-b border-line bg-surface">
      <div className="mx-auto flex max-w-4xl flex-wrap items-center gap-x-6 gap-y-2 px-4 py-3">
        <Link href={`/${locale}`} className="flex items-baseline gap-2">
          <span className="text-lg font-semibold tracking-tight">Fontis</span>
          <span className="hidden text-sm text-muted xl:inline">{t.tagline}</span>
        </Link>
        <nav className="ml-auto flex flex-wrap items-center gap-1 text-sm">
          <NavLink href={`/${locale}`}>{t.nav.ask}</NavLink>
          <NavLink href={`/${locale}/documents`}>{t.docs.nav}</NavLink>
          {admin && <NavLink href={`/${locale}/log`}>{t.nav.log}</NavLink>}
          {admin && <NavLink href={`/${locale}/users`}>{t.users.nav}</NavLink>}
          {ready &&
            (user ? (
              <button
                type="button"
                onClick={async () => {
                  await logout();
                  router.push(`/${locale}`);
                }}
                title={user.email}
                className="rounded-md px-3 py-1.5 text-muted hover:text-ink"
              >
                {user.displayName} · {t.auth.signOut}
              </button>
            ) : (
              <NavLink href={`/${locale}/login`}>{t.auth.signIn}</NavLink>
            ))}
          <LanguageSwitcher locale={locale} />
        </nav>
      </div>
    </header>
  );
}
