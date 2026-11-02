import type { MetadataRoute } from "next";

const site = process.env.NEXT_PUBLIC_SITE_URL ?? "http://localhost:3001";

/** O Google indexa o site e o agendamento; painel, login e descadastro ficam de fora. */
export default function robots(): MetadataRoute.Robots {
  return {
    rules: { userAgent: "*", allow: "/", disallow: ["/painel", "/login", "/sair"] },
    sitemap: `${site}/sitemap.xml`,
  };
}
