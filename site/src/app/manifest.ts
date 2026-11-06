import type { MetadataRoute } from "next";
import { carregarLoja } from "@/lib/lojaPublica";

/**
 * App instalável (PWA): no Chrome/Edge aparece "Instalar aplicativo"; vira ícone na área de trabalho e no celular
 * e abre direto no painel, em janela própria. O nome vem da tela Configurações.
 */
export default async function manifest(): Promise<MetadataRoute.Manifest> {
  const c = await carregarLoja();
  return {
    name: `${c.nome} · Painel`,
    short_name: c.nome.length > 12 ? "Pet Shop" : c.nome,
    description: "Agenda, clientes, vacinas, financeiro e WhatsApp do pet shop.",
    start_url: "/painel",
    scope: "/",
    display: "standalone",
    background_color: "#f8f5ef",
    theme_color: "#25488a",
    lang: "pt-BR",
    icons: [
      { src: "/icone-192.png", sizes: "192x192", type: "image/png" },
      { src: "/icone-512.png", sizes: "512x512", type: "image/png" },
      { src: "/icone-512.png", sizes: "512x512", type: "image/png", purpose: "maskable" },
    ],
  };
}
