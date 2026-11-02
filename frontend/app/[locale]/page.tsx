import { notFound } from "next/navigation";
import Chat from "@/components/Chat";
import { isLocale } from "@/lib/i18n";

export default async function AskPage({ params }: PageProps<"/[locale]">) {
  const { locale } = await params;
  if (!isLocale(locale)) {
    notFound();
  }
  return <Chat locale={locale} />;
}
