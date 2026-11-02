import type { MetadataRoute } from "next";

const site = process.env.NEXT_PUBLIC_SITE_URL ?? "http://localhost:3001";

export default function sitemap(): MetadataRoute.Sitemap {
  return [
    { url: site, changeFrequency: "monthly", priority: 1 },
    { url: `${site}/agendar`, changeFrequency: "weekly", priority: 0.8 },
  ];
}
