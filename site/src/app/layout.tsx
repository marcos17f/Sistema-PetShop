import type { Metadata } from "next";
import "./globals.css";
import { carregarLoja } from "@/lib/lojaPublica";

export async function generateMetadata(): Promise<Metadata> {
  const c = await carregarLoja();
  return {
    title: { default: `${c.nome} | Banho, tosa e vacinas`, template: `%s | ${c.nome}` },
    description: `Banho, tosa e vacinas${c.cidade ? ` em ${c.cidade}` : ""}, com agendamento pelo WhatsApp e lembrete das próximas datas.`,
  };
}

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
