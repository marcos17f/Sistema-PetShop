import type { Metadata } from "next";
import "./globals.css";
import { RegistrarApp } from "@/components/RegistrarApp";
import { loja } from "@/lib/loja";
import { carregarLoja } from "@/lib/lojaPublica";

export async function generateMetadata(): Promise<Metadata> {
  const c = await carregarLoja();
  const descricao = `Banho, tosa e cuidados pet${c.cidade ? ` em ${c.cidade}` : ""}. Mais de 20 anos de experiência, com agendamento pelo WhatsApp e lembrete do próximo banho.`;
  return {
    metadataBase: new URL(process.env.NEXT_PUBLIC_SITE_URL ?? "http://localhost:3001"),
    title: { default: `${c.nome} | Banho, tosa e cuidados pet`, template: `%s | ${c.nome}` },
    icons: { icon: "/favicon.png", apple: "/icone-192.png" },
    appleWebApp: { capable: true, title: c.nome, statusBarStyle: "default" },
    description: descricao,
    openGraph: {
      type: "website",
      locale: "pt_BR",
      siteName: c.nome,
      title: `${c.nome} | ${loja.slogan}`,
      description: descricao,
      images: [{ url: "/logo-arca.jpg", width: 150, height: 150, alt: `Logo ${c.nome}` }],
    },
  };
}

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="pt-BR">
      <head>
        <link rel="preconnect" href="https://fonts.googleapis.com" />
        <link rel="preconnect" href="https://fonts.gstatic.com" crossOrigin="" />
        <link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Fredoka:wght@500;600;700&family=Plus+Jakarta+Sans:wght@400;500;600;700;800&display=swap" />
      </head>
      <body className="antialiased">
        <RegistrarApp />
        {children}
      </body>
    </html>
  );
}
