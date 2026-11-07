"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { Marca } from "@/components/Pata";
import { buscarConfig, estaLogado, listarAvisos, modoDemonstracao, quemSouEu, sair } from "@/lib/api";
import type { Aviso, Eu } from "@/lib/tipos";

/** soDono: o funcionário não vê (a API também bloqueia). */
const menu = [
  { href: "/painel", texto: "Agenda" },
  { href: "/painel/clientes", texto: "Clientes e pets" },
  { href: "/painel/lembretes", texto: "Lembretes no WhatsApp" },
  { href: "/painel/financeiro", texto: "Financeiro", soDono: true },
  { href: "/painel/whatsapp", texto: "WhatsApp" },
  { href: "/painel/campanhas", texto: "Campanhas", soDono: true },
  { href: "/painel/configuracoes", texto: "Configurações", soDono: true },
  { href: "/painel/usuarios", texto: "Usuários", soDono: true },
  { href: "/painel/saude", texto: "Saúde do sistema", soDono: true },
];

/** Moldura do painel: menu lateral + guarda de login (como um AuthGuard no Angular). */
export default function PainelLayout({ children }: { children: React.ReactNode }) {
  const caminho = usePathname();
  const router = useRouter();
  const [pronto, setPronto] = useState(false);
  const [nomeLoja, setNomeLoja] = useState<string | undefined>();
  const [avisos, setAvisos] = useState<Aviso[]>([]);
  const [eu, setEu] = useState<Eu | null>(null);

  useEffect(() => {
    if (!estaLogado()) router.replace("/login");
    else {
      setPronto(true);
      buscarConfig().then((c) => setNomeLoja(c.nome)).catch(() => undefined); // sem nome novo: fica o padrão
      quemSouEu().then(setEu).catch(() => undefined);
    }
  }, [router]);

  // avisos do sistema: ao trocar de tela e a cada 5 minutos
  useEffect(() => {
    if (!pronto || eu?.papel !== "DONO") return; // avisos do sistema são para o dono
    const ler = () => listarAvisos().then(setAvisos).catch(() => undefined);
    ler();
    const t = setInterval(ler, 5 * 60_000);
    return () => clearInterval(t);
  }, [pronto, caminho, eu]);

  if (!pronto) return null;
  const importantes = avisos.filter((x) => x.nivel !== "INFO");

  return (
    <div className="flex min-h-screen flex-wrap">
      {/* em tela grande a barra fica presa na altura da tela: "Sair" sempre visível sem rolar */}
      <aside className="flex max-w-full flex-[1_1_220px] flex-col gap-7 bg-verde-escuro px-5 py-7 text-menta lg:sticky lg:top-0 lg:h-screen lg:overflow-y-auto">
        <Marca nome={nomeLoja} subtitulo="Painel da loja" />
        <nav aria-label="Menu do painel" className="flex flex-col gap-1 text-[15px] font-semibold">
          {menu.filter((m) => !m.soDono || eu?.papel === "DONO").map((m) => {
            const ativo = m.href === "/painel" ? caminho === "/painel" : caminho.startsWith(m.href);
            return (
              <Link
                key={m.href}
                href={m.href}
                aria-current={ativo ? "page" : undefined}
                className={`flex min-h-11 items-center rounded-xl px-3.5 no-underline ${ativo ? "bg-menta text-verde-escuro" : "text-menta hover:bg-verde-linha"}`}
              >
                {m.texto}
              </Link>
            );
          })}
        </nav>
        <div className="mt-auto flex flex-col gap-2 text-xs text-menta-2">
          {modoDemonstracao && <span>Modo demonstração: dados de exemplo</span>}
          {eu && !modoDemonstracao && (
            <span className="text-sm text-menta">
              {eu.nome} · {eu.papel === "DONO" ? "dono" : "funcionário"}
            </span>
          )}
          {!modoDemonstracao && (
            <Link href="/painel/minha-senha" className="self-start text-sm font-semibold text-menta underline">Minha senha</Link>
          )}
          {!modoDemonstracao && (
            <button
              type="button"
              onClick={() => {
                sair();
                router.replace("/login");
              }}
              className="btn mt-1 w-full border-menta-2 text-white hover:bg-verde-linha"
            >
              <span aria-hidden="true">↪</span> Sair
            </button>
          )}
        </div>
      </aside>
      <main className="flex min-w-0 flex-[999_1_640px] flex-col gap-6 p-6 sm:p-8">
        {importantes.length > 0 && caminho !== "/painel/saude" && (
          <Link
            href="/painel/saude"
            role="alert"
            className={`flex flex-wrap items-center gap-2 rounded-xl px-4 py-3 font-semibold no-underline ${importantes.some((x) => x.nivel === "CRITICO") ? "bg-[#fbdad3] text-[#9b2c12]" : "bg-[#fdecc8] text-[#7a4a00]"}`}
          >
            <span>⚠ {importantes[0].titulo}</span>
            {importantes.length > 1 && <span className="font-medium">e mais {importantes.length - 1} aviso(s)</span>}
            <span className="ml-auto underline">Ver o que fazer</span>
          </Link>
        )}
        {children}
      </main>
    </div>
  );
}
