import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import "../globals.css";
import { geistMono, geistSans } from "../fonts";
import { dictionaries, isLocale, locales } from "@/lib/i18n";
import LanguageSwitcher from "@/components/LanguageSwitcher";
import NavLink from "@/components/NavLink";

export function generateStaticParams() {
  return locales.map((locale) => ({ locale }));
}

export async function generateMetadata({ params }: LayoutProps<"/[locale]">): Promise<Metadata> {
  const { locale } = await params;
  return { title: "Fontis", description: isLocale(locale) ? dictionaries[locale].tagline : undefined };
}

export default async function LocaleLayout({ children, params }: LayoutProps<"/[locale]">) {
  const { locale } = await params;
  if (!isLocale(locale)) {
    notFound();
  }
  const t = dictionaries[locale];

  return (
    <html lang={locale} className={`${geistSans.variable} ${geistMono.variable} antialiased`}>
      <body className="flex min-h-dvh flex-col">
        <header className="border-b border-line bg-surface">
          <div className="mx-auto flex max-w-4xl flex-wrap items-center gap-x-6 gap-y-2 px-4 py-3">
            <Link href={`/${locale}`} className="flex items-baseline gap-2">
              <span className="text-lg font-semibold tracking-tight">Fontis</span>
              <span className="hidden text-sm text-muted sm:inline">{t.tagline}</span>
            </Link>
            <nav className="ml-auto flex items-center gap-1 text-sm">
              <NavLink href={`/${locale}`}>{t.nav.ask}</NavLink>
              <NavLink href={`/${locale}/log`}>{t.nav.log}</NavLink>
              <LanguageSwitcher locale={locale} />
            </nav>
          </div>
        </header>

        {/* AI Act Art. 50: people must know they are interacting with an AI system. */}
        <div role="note" className="border-b border-line bg-accent-soft">
          <p className="mx-auto max-w-4xl px-4 py-2.5 text-sm leading-relaxed">
            <span aria-hidden className="mr-1.5">ⓘ</span>
            {t.disclosure}
          </p>
        </div>

        <main className="mx-auto w-full max-w-4xl flex-1 px-4 py-8">{children}</main>

        <footer className="border-t border-line">
          <p className="mx-auto max-w-4xl px-4 py-4 text-xs leading-relaxed text-muted">{t.footer}</p>
        </footer>
      </body>
    </html>
  );
}
