import type { NextConfig } from "next";

/** Cabeçalhos de segurança em todas as páginas (o painel não pode ser aberto dentro de outro site). */
const seguranca = [
  { key: "X-Frame-Options", value: "DENY" },
  { key: "X-Content-Type-Options", value: "nosniff" },
  { key: "Referrer-Policy", value: "strict-origin-when-cross-origin" },
  { key: "Permissions-Policy", value: "camera=(), microphone=(), geolocation=()" },
];

const nextConfig: NextConfig = {
  poweredByHeader: false,
  async headers() {
    return [{ source: "/:path*", headers: seguranca }];
  },
};

export default nextConfig;
