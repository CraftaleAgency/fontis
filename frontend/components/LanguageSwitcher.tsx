"use client";

import { usePathname, useRouter } from "next/navigation";
import { languageNames, locales, type Locale } from "@/lib/i18n";

/** Switches language and stays on the same page. */
export default function LanguageSwitcher({ locale }: { locale: Locale }) {
  const router = useRouter();
  const pathname = usePathname();
  return (
    <select
      aria-label="Language"
      value={locale}
      onChange={(event) => router.push(pathname.replace(/^\/[a-z]{2}/, `/${event.target.value}`))}
      className="ml-2 rounded-md border border-line bg-surface px-2 py-1.5 text-sm"
    >
      {locales.map((l) => (
        <option key={l} value={l}>
          {languageNames[l]}
        </option>
      ))}
    </select>
  );
}
