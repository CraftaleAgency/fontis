import { notFound } from "next/navigation";
import DocumentsPage from "@/components/DocumentsPage";
import { isLocale } from "@/lib/i18n";

export default async function Page({ params }: PageProps<"/[locale]/documents">) {
  const { locale } = await params;
  if (!isLocale(locale)) {
    notFound();
  }
  return <DocumentsPage locale={locale} />;
}
