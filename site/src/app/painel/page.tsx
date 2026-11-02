"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { ChipStatus, Carregando, Erro, Titulo } from "@/components/Ui";
import { criarAgendamento, listarAgenda, listarLembretes, listarTutores, mudarStatus, type NovoAgendamento } from "@/lib/api";
import { diaPorExtenso, hoje, somarDias } from "@/lib/datas";
import { NOMES_SERVICO, NOMES_STATUS, lerValor, type Agendamento, type Lembrete, type StatusAgendamento, type TipoServico, type Tutor } from "@/lib/tipos";

const FILTROS: { nome: string; tipos: TipoServico[] | null }[] = [
  { nome: "Todos", tipos: null },
  { nome: "Banho e tosa", tipos: ["BANHO", "TOSA", "BANHO_E_TOSA"] },
  { nome: "Vacinas", tipos: ["VACINA"] },
  { nome: "Consultas", tipos: ["CONSULTA"] },
];

export default function Agenda() {
  const [data, setData] = useState(hoje());
  const [filtro, setFiltro] = useState(0);
  const [itens, setItens] = useState<Agendamento[] | null>(null);
  const [lembretes, setLembretes] = useState<Lembrete[]>([]);
  const [erro, setErro] = useState<string | null>(null);
  const [novo, setNovo] = useState(false);

  const carregar = useCallback(async () => {
    setErro(null);
    try {
      const [a, l] = await Promise.all([listarAgenda(data), listarLembretes(somarDias(hoje(), 1))]);
      setItens(a);
      setLembretes(l);
    } catch (e) {
      setErro((e as Error).message);
    }
  }, [data]);

  useEffect(() => {
    setItens(null);
    carregar();
  }, [carregar]);

  async function trocarStatus(a: Agendamento, status: StatusAgendamento, valor?: number) {
    try {
      const atualizado = await mudarStatus(a.id, status, valor);
      setItens((lista) => lista?.map((x) => (x.id === a.id ? atualizado : x)) ?? null);
      // concluir um banho muda a data do próximo banho, então os lembretes mudam junto
      setLembretes(await listarLembretes(somarDias(hoje(), 1)));
    } catch (e) {
      setErro((e as Error).message);
    }
  }

  const tipos = FILTROS[filtro].tipos;
  const visiveis = itens?.filter((a) => !tipos || tipos.includes(a.tipo)) ?? [];
  const pendentes = lembretes.filter((l) => !l.enviado).length;

  return (
    <>
      <Titulo sobre={diaPorExtenso(data)} titulo={data === hoje() ? "Agenda de hoje" : "Agenda"}>
        <div className="flex flex-wrap gap-2">
          <button type="button" className="btn btn-claro" onClick={() => setData(somarDias(data, -1))} aria-label="Dia anterior">←</button>
          <button type="button" className="btn btn-claro" onClick={() => setData(hoje())}>Hoje</button>
          <button type="button" className="btn btn-claro" onClick={() => setData(somarDias(data, 1))} aria-label="Próximo dia">→</button>
          <button type="button" className="btn btn-verde" onClick={() => setNovo((v) => !v)}>+ Novo agendamento</button>
        </div>
      </Titulo>

      {novo && (
        <NovoAgendamentoForm
          data={data}
          aoSalvar={() => {
            setNovo(false);
            carregar();
          }}
        />
      )}

      <div className="grid grid-cols-[repeat(auto-fit,minmax(200px,1fr))] gap-3.5">
        <div className="cartao flex flex-col gap-1">
          <span className="text-[13px] font-semibold text-muted">Atendimentos no dia</span>
          <span className="text-[32px] font-extrabold">{itens?.filter((a) => a.status !== "CANCELADO").length ?? "—"}</span>
        </div>
        <Link href="/painel/lembretes" className="cartao flex flex-col gap-1 text-ink no-underline">
          <span className="text-[13px] font-semibold text-muted">Lembretes de amanhã para enviar</span>
          <span className="text-[32px] font-extrabold">{pendentes}</span>
        </Link>
      </div>

      <Erro texto={erro} />

      <section aria-label="Agenda" className="cartao flex flex-col gap-3.5">
        <div role="tablist" aria-label="Filtrar" className="flex flex-wrap gap-2">
          {FILTROS.map((f, i) => (
            <button
              key={f.nome}
              type="button"
              role="tab"
              aria-selected={filtro === i}
              onClick={() => setFiltro(i)}
              className={`min-h-10 rounded-full border px-3.5 text-sm font-bold ${filtro === i ? "border-verde bg-verde text-white" : "border-borda bg-white text-ink"}`}
            >
              {f.nome}
            </button>
          ))}
        </div>
        {!itens ? (
          <Carregando />
        ) : visiveis.length === 0 ? (
          <p className="m-0 py-6 text-muted">Nenhum atendimento neste dia.</p>
        ) : (
          <ul className="m-0 flex list-none flex-col p-0">
            {visiveis.map((a) => (
              <li key={a.id} className="flex flex-wrap items-center gap-x-4 gap-y-2 border-b border-linha px-1 py-3.5 last:border-0">
                <span className="w-14 text-base font-extrabold">{a.hora}</span>
                <span className="flex min-w-0 flex-[1_1_200px] flex-col leading-tight">
                  <span className="font-bold">
                    {a.petNome} <span className="font-medium text-muted">· {a.tutorNome}</span>
                  </span>
                  <span className="text-sm text-muted">{a.descricao || NOMES_SERVICO[a.tipo]}</span>
                </span>
                <ChipStatus status={a.status} />
                {a.status === "CONCLUIDO" && (
                  <ValorCobrado key={`${a.id}-${a.valor}`} a={a} aoSalvar={(v) => trocarStatus(a, "CONCLUIDO", v)} />
                )}
                <label className="sr-only" htmlFor={`st-${a.id}`}>Mudar situação</label>
                <select id={`st-${a.id}`} className="campo w-auto min-h-10" value={a.status} onChange={(e) => trocarStatus(a, e.target.value as StatusAgendamento)}>
                  {Object.entries(NOMES_STATUS).map(([v, t]) => (
                    <option key={v} value={v}>{t}</option>
                  ))}
                </select>
              </li>
            ))}
          </ul>
        )}
      </section>
    </>
  );
}

/** Valor cobrado no atendimento concluído: vem com o preço padrão e salva ao sair do campo. */
function ValorCobrado({ a, aoSalvar }: { a: Agendamento; aoSalvar: (valor: number) => void }) {
  const [texto, setTexto] = useState(a.valor === null ? "" : a.valor.toFixed(2).replace(".", ","));
  function salvar() {
    const v = lerValor(texto);
    if (v !== null && v !== a.valor) aoSalvar(v);
  }
  return (
    <label className="flex items-center gap-1.5 text-sm font-semibold text-muted">
      R$
      <input
        className="campo min-h-10 w-24"
        inputMode="decimal"
        aria-label={`Valor cobrado de ${a.petNome}`}
        placeholder="0,00"
        value={texto}
        onChange={(e) => setTexto(e.target.value)}
        onBlur={salvar}
        onKeyDown={(e) => e.key === "Enter" && e.currentTarget.blur()}
      />
    </label>
  );
}

function NovoAgendamentoForm({ data, aoSalvar }: { data: string; aoSalvar: () => void }) {
  const [tutores, setTutores] = useState<Tutor[]>([]);
  const [form, setForm] = useState<NovoAgendamento>({ petId: 0, data, hora: "09:00", tipo: "BANHO", descricao: "" });
  const [erro, setErro] = useState<string | null>(null);
  const [salvando, setSalvando] = useState(false);

  useEffect(() => {
    listarTutores().then(setTutores).catch((e: Error) => setErro(e.message));
  }, []);

  async function salvar(e: React.FormEvent) {
    e.preventDefault();
    if (!form.petId) return setErro("Escolha o pet.");
    setSalvando(true);
    try {
      await criarAgendamento(form);
      aoSalvar();
    } catch (er) {
      setErro((er as Error).message);
    } finally {
      setSalvando(false);
    }
  }

  return (
    <form onSubmit={salvar} className="cartao grid grid-cols-[repeat(auto-fit,minmax(170px,1fr))] items-end gap-3.5">
      <label className="flex flex-col gap-1.5">
        <span className="rotulo">Pet</span>
        <select className="campo" value={form.petId} onChange={(e) => setForm({ ...form, petId: Number(e.target.value) })}>
          <option value={0}>Selecione</option>
          {tutores.flatMap((t) => t.pets.map((p) => (
            <option key={p.id} value={p.id}>{p.nome} ({t.nome})</option>
          )))}
        </select>
      </label>
      <label className="flex flex-col gap-1.5">
        <span className="rotulo">Serviço</span>
        <select className="campo" value={form.tipo} onChange={(e) => setForm({ ...form, tipo: e.target.value as TipoServico })}>
          {Object.entries(NOMES_SERVICO).map(([v, t]) => (
            <option key={v} value={v}>{t}</option>
          ))}
        </select>
      </label>
      <label className="flex flex-col gap-1.5">
        <span className="rotulo">Data</span>
        <input className="campo" type="date" value={form.data} onChange={(e) => setForm({ ...form, data: e.target.value })} />
      </label>
      <label className="flex flex-col gap-1.5">
        <span className="rotulo">Hora</span>
        <input className="campo" type="time" value={form.hora} onChange={(e) => setForm({ ...form, hora: e.target.value })} />
      </label>
      <label className="flex flex-col gap-1.5">
        <span className="rotulo">Detalhe (opcional)</span>
        <input className="campo" placeholder="Ex.: hidratação" value={form.descricao} onChange={(e) => setForm({ ...form, descricao: e.target.value })} />
      </label>
      <button type="submit" disabled={salvando} className="btn btn-verde">{salvando ? "Salvando…" : "Salvar"}</button>
      <div className="col-span-full"><Erro texto={erro} /></div>
    </form>
  );
}
