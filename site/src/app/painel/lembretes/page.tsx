"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { Carregando, Erro, Titulo } from "@/components/Ui";
import { listarLembretes, marcarEnviado } from "@/lib/api";
import { diaPorExtenso, hoje, somarDias } from "@/lib/datas";
import { linkWhatsapp } from "@/lib/loja";
import type { Lembrete, TipoLembrete } from "@/lib/tipos";

const ROTULO: Record<TipoLembrete, string> = { VACINA: "Vacina", BANHO: "Retorno de banho", AGENDAMENTO: "Agendamento" };

/**
 * Lembretes da véspera. Envio por clique: abre o WhatsApp com a mensagem pronta (custo zero).
 * Com a API oficial do WhatsApp ligada no Spring Boot, o envio passa a ser automático às 18h.
 */
export default function Lembretes() {
  const data = somarDias(hoje(), 1);
  const [lista, setLista] = useState<Lembrete[] | null>(null);
  const [erro, setErro] = useState<string | null>(null);

  useEffect(() => {
    listarLembretes(data).then(setLista).catch((e: Error) => setErro(e.message));
  }, [data]);

  async function enviar(l: Lembrete) {
    window.open(linkWhatsapp(l.whatsapp, l.mensagem), "_blank", "noopener");
    try {
      await marcarEnviado(l.chave);
      setLista((ls) => ls?.map((x) => (x.chave === l.chave ? { ...x, enviado: true } : x)) ?? null);
    } catch (e) {
      setErro((e as Error).message);
    }
  }

  const pendentes = lista?.filter((l) => !l.enviado).length ?? 0;

  return (
    <>
      <Titulo sobre={`Para ${diaPorExtenso(data).toLowerCase()}`} titulo="Lembretes de amanhã" />
      <p className="m-0 max-w-[640px] text-muted">
        Clique em <strong>Enviar no WhatsApp</strong>: a conversa abre com a mensagem pronta, é só apertar enviar.
        {lista && ` ${pendentes} de ${lista.length} ainda não enviados.`}
      </p>
      <Erro texto={erro} />
      {!lista ? (
        <Carregando />
      ) : lista.length === 0 ? (
        <p className="cartao m-0 text-muted">Nenhum lembrete para amanhã.</p>
      ) : (
        <ul className="m-0 grid list-none grid-cols-[repeat(auto-fill,minmax(320px,1fr))] gap-3.5 p-0">
          {lista.map((l) => (
            <li key={l.chave} className="cartao flex flex-col gap-3">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <Link href={`/painel/clientes`} className="font-extrabold text-ink no-underline">
                  {l.petNome} <span className="font-medium text-muted">· {l.tutorNome}</span>
                </Link>
                <span className="chip bg-fundo text-muted">{ROTULO[l.tipo]}</span>
              </div>
              <p className="m-0 rounded-2xl rounded-tl-sm bg-[#d9fdd3] p-3 text-[15px]">{l.mensagem}</p>
              {l.enviado ? (
                <span className="text-sm font-bold text-[#0b5e4f]">Enviado</span>
              ) : (
                <button type="button" onClick={() => enviar(l)} className="btn btn-verde self-start">Enviar no WhatsApp</button>
              )}
            </li>
          ))}
        </ul>
      )}
    </>
  );
}
