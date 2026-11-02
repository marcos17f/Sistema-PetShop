/**
 * Configurações da loja para o site público (renderizado no servidor). Lê a API sem login e guarda por 60 s;
 * se a API não estiver configurada ou fora do ar, usa os valores de exemplo para o site nunca ficar em branco.
 */
import { configExemplo, loja } from "./loja";
import type { ConfigLoja } from "./tipos";

export async function carregarLoja(): Promise<ConfigLoja> {
  const api = process.env.NEXT_PUBLIC_API_URL;
  if (!api) return configExemplo;
  try {
    const res = await fetch(`${api}/api/publico/lojas/${loja.slug}`, { next: { revalidate: 60 } });
    if (res.ok) return (await res.json()) as ConfigLoja;
  } catch {
    /* API fora do ar: segue com os valores de exemplo */
  }
  return configExemplo;
}
