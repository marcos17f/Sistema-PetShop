import type { Metadata } from "next";
import "./globals.css";
import { loja } from "@/lib/loja";

export const metadata: Metadata = {
  title: { default: `${loja.nome} | Banho, tosa e vacinas`, template: `%s | ${loja.nome}` },
  description: "Banho, tosa e vacinas com agendamento pelo WhatsApp e lembrete das próximas datas.",
};

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="pt-BR">
      <head>
        <link rel="preconnect" href="https://fonts.googleapis.com" />
        <link rel="preconnect" href="https://fonts.gstatic.com" crossOrigin="" />
        <link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@400;500;600;700;800&display=swap" />
      </head>
      <body className="antialiased">{children}</body>
    </html>
  );
}
