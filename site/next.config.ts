import type { NextConfig } from "next";

const dev = process.env.NODE_ENV !== "production";
const api = process.env.NEXT_PUBLIC_API_URL ?? "";

/**
 * Política de conteúdo (CSP): o navegador só fala com o próprio site e com a API. Se um dia entrar um script estranho,
 * ele não consegue mandar o login nem dados de clientes para fora (connect-src), nem carregar código de outro lugar.
 * shortcut: script-src com 'unsafe-inline' porque o Next coloca scripts inline na página; trocar por nonce (middleware)
 * se o site passar a mostrar conteúdo de terceiros.
 */
const csp = [
  "default-src 'self'",
  `script-src 'self' 'unsafe-inline'${dev ? " 'unsafe-eval'" : ""}`,
  "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com",
  "font-src 'self' https://fonts.gstatic.com",
  "img-src 'self' data: blob:",
  `connect-src 'self'${api ? ` ${api}` : ""}${dev ? " ws:" : ""}`,
  "frame-src https://www.google.com", // mapa da loja
  "frame-ancestors 'none'",
  "object-src 'none'",
  "base-uri 'self'",
  "form-action 'self'",
  ...(dev ? [] : ["upgrade-insecure-requests"]),
].join("; ");

/** Cabeçalhos de segurança em todas as páginas (o painel não pode ser aberto dentro de outro site). */
const seguranca = [
  { key: "Content-Security-Policy", value: csp },
  { key: "Strict-Transport-Security", value: "max-age=63072000; includeSubDomains" },
  { key: "X-Frame-Options", value: "DENY" },
  { key: "X-Content-Type-Options", value: "nosniff" },
  { key: "Referrer-Policy", value: "strict-origin-when-cross-origin" },
  { key: "Permissions-Policy", value: "camera=(), microphone=(), geolocation=()" },
  { key: "Cross-Origin-Opener-Policy", value: "same-origin" },
];

const nextConfig: NextConfig = {
  poweredByHeader: false,
  async headers() {
    return [
      { source: "/:path*", headers: seguranca },
      // o navegador sempre confere se há versão nova do service worker
      { source: "/sw.js", headers: [{ key: "Cache-Control", value: "no-cache" }, { key: "Service-Worker-Allowed", value: "/" }] },
    ];
  },
};

export default nextConfig;
