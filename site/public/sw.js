// Service worker mínimo do app instalável.
// Segurança/LGPD: NÃO guarda páginas, respostas da API nem dados de clientes no computador.
// Só a página "sem internet", mostrada quando a conexão cai durante a navegação.
const CACHE = "petshop-offline-v1";
const OFFLINE = "/offline.html";

self.addEventListener("install", (e) => {
  e.waitUntil(caches.open(CACHE).then((c) => c.add(OFFLINE)).then(() => self.skipWaiting()));
});

self.addEventListener("activate", (e) => {
  e.waitUntil(
    caches.keys()
      .then((nomes) => Promise.all(nomes.filter((n) => n !== CACHE).map((n) => caches.delete(n))))
      .then(() => self.clients.claim()),
  );
});

self.addEventListener("fetch", (e) => {
  if (e.request.mode !== "navigate") return; // API, imagens e scripts seguem direto para a rede
  e.respondWith(fetch(e.request).catch(() => caches.match(OFFLINE)));
});
