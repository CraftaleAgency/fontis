import { notFound } from "next/navigation";
import LoginForm from "@/components/LoginForm";
import { isLocale } from "@/lib/i18n";

export default async function Page({ params }: PageProps<"/[locale]/login">) {
  const { locale } = await params;
  if (!isLocale(locale)) {
    notFound();
  }
  return <LoginForm locale={locale} />;
}
