"use client";

import { useEffect } from "react";

/** Registra o service worker do app instalável (só no site publicado; no desenvolvimento atrapalharia o recarregamento). */
export function RegistrarApp() {
  useEffect(() => {
    if (process.env.NODE_ENV !== "production" || !("serviceWorker" in navigator)) return;
    navigator.serviceWorker.register("/sw.js").catch(() => undefined); // sem service worker o sistema funciona igual
  }, []);
  return null;
}
