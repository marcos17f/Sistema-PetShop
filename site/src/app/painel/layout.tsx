"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { Marca } from "@/components/Pata";
import { estaLogado, modoDemonstracao, sair } from "@/lib/api";

const menu = [
  { href: "/painel", texto: "Agenda" },
  { href: "/painel/clientes", texto: "Clientes e pets" },
  { href: "/painel/lembretes", texto: "Lembretes no WhatsApp" },
  { href: "/painel/financeiro", texto: "Financeiro" },
];

/** Moldura do painel: menu lateral + guarda de login (como um AuthGuard no Angular). */
export default function PainelLayout({ children }: { children: React.ReactNode }) {
  const caminho = usePathname();
  const router = useRouter();
  const [pronto, setPronto] = useState(false);

  useEffect(() => {
    if (!estaLogado()) router.replace("/login");
    else setPronto(true);
  }, [router]);

  if (!pronto) return null;

  return (
    <div className="flex min-h-screen flex-wrap">
      <aside className="flex max-w-full flex-[1_1_220px] flex-col gap-7 bg-verde-escuro px-5 py-7 text-menta">
        <Marca subtitulo="Painel da loja" />
        <nav aria-label="Menu do painel" className="flex flex-col gap-1 text-[15px] font-semibold">
          {menu.map((m) => {
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
          {!modoDemonstracao && (
            <button
              type="button"
              onClick={() => {
                sair();
                router.replace("/login");
              }}
              className="self-start text-sm font-semibold text-menta underline"
            >
              Sair
            </button>
          )}
        </div>
      </aside>
      <main className="flex min-w-0 flex-[999_1_640px] flex-col gap-6 p-6 sm:p-8">{children}</main>
    </div>
  );
}
