"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { isLocale } from "@/lib/i18n";

/** Sends the visitor to their browser language, or English. */
export default function LanguageRedirect() {
  const router = useRouter();
  useEffect(() => {
    const preferred = navigator.languages.map((l) => l.slice(0, 2).toLowerCase()).find(isLocale);
    router.replace(`/${preferred ?? "en"}`);
  }, [router]);
  return null;
}
