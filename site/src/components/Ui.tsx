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
