import { notFound } from "next/navigation";
import AnswerLog from "@/components/AnswerLog";
import { isLocale } from "@/lib/i18n";

export default async function LogPage({ params }: PageProps<"/[locale]/log">) {
  const { locale } = await params;
  if (!isLocale(locale)) {
    notFound();
  }
  return <AnswerLog locale={locale} />;
}
