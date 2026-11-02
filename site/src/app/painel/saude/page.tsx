"use client";

import { useEffect, useState } from "react";
import { Carregando, Erro, Titulo } from "@/components/Ui";
import { listarAvisos } from "@/lib/api";
import type { Aviso } from "@/lib/tipos";

const ESTILO: Record<Aviso["nivel"], { cor: string; nome: string }> = {
  CRITICO: { cor: "border-[#c2410c] bg-[#fbefe9]", nome: "Urgente" },
  ATENCAO: { cor: "border-[#c97f1f] bg-[#fdf3e1]", nome: "Atenção" },
  INFO: { cor: "border-[#9fb0ac] bg-white", nome: "Informação" },
};

/** Avisos para a loja trabalhar tranquila: o que precisa de atenção antes de algo parar, e o que fazer. */
export default function Saude() {
  const [avisos, setAvisos] = useState<Aviso[] | null>(null);
  const [erro, setErro] = useState<string | null>(null);

  useEffect(() => {
    listarAvisos().then(setAvisos).catch((e: Error) => setErro(e.message));
  }, []);

  return (
    <>
      <Titulo sobre="Painel da loja" titulo="Saúde do sistema" />
      <p className="m-0 max-w-[760px] text-muted">
        O sistema confere sozinho limites do plano de WhatsApp, espaço do banco, memória do servidor, erros e segurança,
        e avisa aqui antes de algo parar. Cada aviso diz o que fazer.
      </p>
      <Erro texto={erro} />
      {!avisos ? (
        !erro && <Carregando />
      ) : avisos.length === 0 ? (
        <section className="cartao bg-[#ddf2ea]">
          <h2 className="m-0 text-lg font-extrabold text-[#0b5e4f]">Tudo certo</h2>
          <p className="m-0 mt-1 text-[#0b5e4f]">Nenhum aviso no momento. O sistema está funcionando normalmente.</p>
        </section>
      ) : (
        <ul className="m-0 flex list-none flex-col gap-3 p-0">
          {avisos.map((a, i) => (
            <li key={i} className={`flex flex-col gap-1.5 rounded-2xl border-l-4 p-5 ${ESTILO[a.nivel].cor}`}>
              <span className="text-xs font-bold uppercase tracking-wider text-muted">{ESTILO[a.nivel].nome} · {a.area}</span>
              <h2 className="m-0 text-lg font-extrabold">{a.titulo}</h2>
              <p className="m-0">{a.detalhe}</p>
              <p className="m-0 font-semibold">O que fazer: {a.acao}</p>
            </li>
          ))}
        </ul>
      )}
    </>
  );
}
