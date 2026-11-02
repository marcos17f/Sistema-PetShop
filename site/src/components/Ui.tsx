import { useState } from "react";
import type { StatusAgendamento } from "@/lib/tipos";
import { NOMES_STATUS } from "@/lib/tipos";

const CORES_STATUS: Record<StatusAgendamento, string> = {
  AGUARDANDO: "bg-[#fdecc8] text-[#7a4a00]",
  CONFIRMADO: "bg-[#ddf2ea] text-[#0b5e4f]",
  EM_ATENDIMENTO: "bg-[#dce7fb] text-[#1e3a8a]",
  CONCLUIDO: "bg-[#e9eceb] text-[#3f4b48]",
  CANCELADO: "bg-[#fbdad3] text-[#9b2c12]",
};

export function ChipStatus({ status }: { status: StatusAgendamento }) {
  return <span className={`chip ${CORES_STATUS[status]}`}>{NOMES_STATUS[status]}</span>;
}

export function Carregando() {
  return <p className="m-0 text-muted" role="status">Carregando…</p>;
}

export function Erro({ texto }: { texto: string | null }) {
  if (!texto) return null;
  return (
    <p role="alert" className="m-0 rounded-xl border border-[#e2b9a8] bg-[#fbefe9] px-4 py-3 text-[15px] text-[#8a3b1e]">
      {texto}
    </p>
  );
}

export function Titulo({ sobre, titulo, children }: { sobre?: string; titulo: string; children?: React.ReactNode }) {
  return (
    <div className="flex flex-wrap items-center justify-between gap-4">
      <div className="flex flex-col gap-0.5">
        {sobre && <p className="m-0 text-sm font-semibold text-muted">{sobre}</p>}
        <h1 className="m-0 text-[32px] font-extrabold tracking-tight">{titulo}</h1>
      </div>
      {children}
    </div>
  );
}

/** "Excluir" que pede confirmação na própria tela (sem janela do navegador), para não apagar por engano. */
export function BotaoExcluir({ pergunta, aoConfirmar }: { pergunta: string; aoConfirmar: () => Promise<void> }) {
  const [confirmando, setConfirmando] = useState(false);
  const [excluindo, setExcluindo] = useState(false);
  if (!confirmando) {
    return <button type="button" className="btn btn-claro min-h-10" onClick={() => setConfirmando(true)}>Excluir</button>;
  }
  return (
    <span role="alertdialog" aria-label={pergunta} className="flex flex-wrap items-center gap-2 rounded-xl bg-[#fbefe9] px-3 py-2 text-sm text-[#8a3b1e]">
      <span className="font-semibold">{pergunta}</span>
      <button
        type="button"
        disabled={excluindo}
        className="btn min-h-9 bg-[#9b2c12] px-3 text-white"
        onClick={async () => {
          setExcluindo(true);
          try {
            await aoConfirmar();
          } finally {
            setExcluindo(false);
            setConfirmando(false);
          }
        }}
      >
        {excluindo ? "Excluindo…" : "Sim, excluir"}
      </button>
      <button type="button" className="btn btn-claro min-h-9 px-3" onClick={() => setConfirmando(false)}>Não</button>
    </span>
  );
}
