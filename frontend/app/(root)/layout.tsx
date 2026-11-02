import type { Metadata } from "next";
import "../globals.css";
import { geistMono, geistSans } from "../fonts";

export const metadata: Metadata = { title: "Fontis" };

// Root layout for "/" only, which redirects to a language. Localised pages use app/[locale]/layout.tsx.
export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en" className={`${geistSans.variable} ${geistMono.variable} antialiased`}>
      <body>{children}</body>
    </html>
  );
}
