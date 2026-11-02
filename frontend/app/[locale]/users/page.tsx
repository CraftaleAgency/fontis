import { notFound } from "next/navigation";
import UsersPage from "@/components/UsersPage";
import { isLocale } from "@/lib/i18n";

export default async function Page({ params }: PageProps<"/[locale]/users">) {
  const { locale } = await params;
  if (!isLocale(locale)) {
    notFound();
  }
  return <UsersPage locale={locale} />;
}
