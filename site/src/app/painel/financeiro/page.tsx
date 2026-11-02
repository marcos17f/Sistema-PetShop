"use client";

import { useCallback, useEffect, useState } from "react";
import { BotaoExcluir, Carregando, Erro, Titulo } from "@/components/Ui";
import { Graficos } from "@/components/Graficos";
import {
  alterarLancamento, buscarResumo, criarCategoria, criarLancamento, definirPreco, excluirLancamento, listarAtendimentos,
  excluirCategoria, listarCategorias, listarHistorico, renomearCategoria, listarLancamentos, listarPrecos, type DadosLancamento,
} from "@/lib/api";
import { formatar, hoje, mesAtual, nomeMes, somarMeses } from "@/lib/datas";
import {
  NOMES_LANCAMENTO, NOMES_SERVICO, lerValor, podeRepetir, reais,
  type Agendamento, type Categoria, type Lancamento, type PrecoServico, type RegistroHistorico, type Resumo, type ResumoMes,
  type TipoLancamento,
} from "@/lib/tipos";

type Aba = TipoLancamento | "PRECOS" | "CATEGORIAS";
const ABAS: { id: Aba; nome: string }[] = [
  { id: "FIXO", nome: "Custos fixos" },
  { id: "VARIAVEL", nome: "Custos variáveis" },
  { id: "EVENTUAL", nome: "Despesas eventuais" },
  { id: "RECEITA", nome: "Receitas" },
  { id: "PRECOS", nome: "Preços dos serviços" },
  { id: "CATEGORIAS", nome: "Categorias" },
];

export default function Financeiro() {
  const [mes, setMes] = useState(mesAtual());
  const [aba, setAba] = useState<Aba>("FIXO");
  const [resumo, setResumo] = useState<Resumo | null>(null);
  const [lancamentos, setLancamentos] = useState<Lancamento[]>([]);
  const [atendimentos, setAtendimentos] = useState<Agendamento[]>([]);
  const [categorias, setCategorias] = useState<Categoria[]>([]);
  const [erro, setErro] = useState<string | null>(null);
  const [exportando, setExportando] = useState(false);
  const [versao, setVersao] = useState(0); // muda a cada recarga, para o histórico se atualizar
  const [novaDespesa, setNovaDespesa] = useState(false);

  const carregar = useCallback(async () => {
    setErro(null);
    try {
      // lançamentos primeiro: abrir o mês lança os recorrentes, e o resumo precisa deles
      const l = await listarLancamentos(mes);
      const [r, a] = await Promise.all([buscarResumo(mes), listarAtendimentos(mes)]);
      setLancamentos(l);
      setResumo(r);
      setAtendimentos(a);
      setVersao((v) => v + 1);
    } catch (e) {
      setErro((e as Error).message);
    }
  }, [mes]);

  useEffect(() => {
    setResumo(null);
    carregar();
  }, [carregar]);

  useEffect(() => {
    listarCategorias().then(setCategorias).catch((e: Error) => setErro(e.message));
  }, []);

  async function exportar() {
    if (!resumo) return;
    setExportando(true);
    try {
      const { exportarMes } = await import("@/lib/exportar");
      await exportarMes(resumo, lancamentos, atendimentos);
    } catch {
      setErro("Não foi possível gerar o arquivo do Excel.");
    } finally {
      setExportando(false);
    }
  }

  return (
    <>
      <Titulo sobre="Financeiro" titulo={nomeMes(mes)}>
        <div className="flex flex-wrap items-center gap-2">
          <button type="button" className="btn btn-claro" onClick={() => setMes(somarMeses(mes, -1))} aria-label="Mês anterior">←</button>
          <label className="sr-only" htmlFor="mes">Mês</label>
          <input id="mes" type="month" className="campo w-auto" value={mes} onChange={(e) => e.target.value && setMes(e.target.value)} />
          <button type="button" className="btn btn-claro" onClick={() => setMes(somarMeses(mes, 1))} aria-label="Próximo mês">→</button>
          <button type="button" className="btn btn-verde" onClick={exportar} disabled={!resumo || exportando}>
            {exportando ? "Gerando…" : "Exportar para Excel"}
          </button>
        </div>
      </Titulo>

      <Erro texto={erro} />

      {!resumo ? <Carregando /> : <Cards atual={resumo.atual} anterior={resumo.anterior} />}
      {resumo && <Graficos meses={resumo.ultimosMeses} />}

      <section aria-label="Lançamentos" className="cartao flex flex-col gap-4">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div role="tablist" aria-label="Tipo de lançamento" className="flex flex-wrap gap-2">
            {ABAS.map((a) => (
              <button
                key={a.id}
                type="button"
                role="tab"
                aria-selected={aba === a.id}
                onClick={() => setAba(a.id)}
                className={`min-h-10 rounded-full border px-3.5 text-sm font-bold ${aba === a.id ? "border-verde bg-verde text-white" : "border-borda bg-white text-ink"}`}
              >
                {a.nome}
              </button>
            ))}
          </div>
          <button type="button" className="btn btn-verde" aria-expanded={novaDespesa} onClick={() => setNovaDespesa((v) => !v)}>
            + Adicionar despesa
          </button>
        </div>

        {novaDespesa && (
          <NovaDespesa
            mes={mes}
            categorias={categorias}
            aoCriarCategoria={(c) => setCategorias((lista) => [...lista, c])}
            aoSalvar={(tipo) => {
              setNovaDespesa(false);
              setAba(tipo); // mostra a despesa na lista certa
              carregar();
            }}
            aoFechar={() => setNovaDespesa(false)}
          />
        )}

        {aba === "PRECOS" ? (
          <Precos />
        ) : aba === "CATEGORIAS" ? (
          <Categorias
            categorias={categorias}
            aoMudar={async () => {
              setCategorias(await listarCategorias());
              carregar(); // nome novo aparece nos lançamentos
            }}
          />
        ) : (
          <Lancamentos
            key={`${aba}-${mes}`}
            tipo={aba}
            mes={mes}
            itens={lancamentos.filter((l) => l.tipo === aba)}
            categorias={categorias.filter((c) => c.tipo === aba)}
            aoCriarCategoria={(c) => setCategorias((lista) => [...lista, c])}
            aoMudar={carregar}
          />
        )}
        {aba === "RECEITA" && <Atendimentos itens={atendimentos} />}
      </section>

      <Historico versao={versao} />
    </>
  );
}

/* ---------- cards do resumo ---------- */

function Cards({ atual, anterior }: { atual: ResumoMes; anterior: ResumoMes }) {
  const positivo = atual.lucro >= 0;
  const cards: { titulo: string; valor: string; antes: string }[] = [
    { titulo: "Receita", valor: reais(atual.receita), antes: reais(anterior.receita) },
    { titulo: "Custos fixos", valor: reais(atual.totalFixo), antes: reais(anterior.totalFixo) },
    { titulo: "Custos variáveis", valor: reais(atual.totalVariavel), antes: reais(anterior.totalVariavel) },
    { titulo: "Despesas eventuais", valor: reais(atual.totalEventual), antes: reais(anterior.totalEventual) },
    { titulo: "Atendimentos concluídos", valor: String(atual.atendimentos), antes: String(anterior.atendimentos) },
    { titulo: "Custo médio por atendimento", valor: reais(atual.custoMedioPorAtendimento), antes: reais(anterior.custoMedioPorAtendimento) },
  ];
  return (
    <div className="grid grid-cols-[repeat(auto-fit,minmax(190px,1fr))] gap-3.5">
      <div className={`cartao flex flex-col gap-1 sm:col-span-2 ${positivo ? "bg-[#ddf2ea] text-[#0b5e4f]" : "bg-[#fbdad3] text-[#9b2c12]"}`}>
        <span className="text-[13px] font-bold">{positivo ? "Lucro do mês" : "Prejuízo do mês"}</span>
        <span className="text-[40px] leading-tight font-extrabold">{reais(atual.lucro)}</span>
        <span className="text-sm font-semibold">
          Receita {reais(atual.receita)} − despesas {reais(atual.custos)} · {nomeMes(anterior.mes, true)}: {reais(anterior.lucro)}
        </span>
      </div>
      {cards.map((c) => (
        <div key={c.titulo} className="cartao flex flex-col gap-1">
          <span className="text-[13px] font-semibold text-muted">{c.titulo}</span>
          <span className="text-[26px] font-extrabold">{c.valor}</span>
          <span className="text-[13px] text-muted">{nomeMes(anterior.mes, true)}: {c.antes}</span>
        </div>
      ))}
    </div>
  );
}

/* ---------- botão "Adicionar despesa": escolhe o tipo e usa o mesmo formulário das abas ---------- */

const TIPOS_DESPESA: { id: TipoLancamento; nome: string; exemplo: string }[] = [
  { id: "FIXO", nome: "Fixa", exemplo: "aluguel, luz, salário" },
  { id: "VARIAVEL", nome: "Variável", exemplo: "shampoo, gasolina, freelancer" },
  { id: "EVENTUAL", nome: "Eventual", exemplo: "equipamento, conserto, reforma" },
];

function NovaDespesa({ mes, categorias, aoCriarCategoria, aoSalvar, aoFechar }: {
  mes: string;
  categorias: Categoria[];
  aoCriarCategoria: (c: Categoria) => void;
  aoSalvar: (tipo: TipoLancamento) => void;
  aoFechar: () => void;
}) {
  const [tipo, setTipo] = useState<TipoLancamento>("VARIAVEL");
  return (
    <section aria-label="Adicionar despesa" className="cartao flex flex-col gap-3 border-2 border-verde">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h2 className="m-0 text-lg font-extrabold">Adicionar despesa</h2>
        <button type="button" className="btn btn-claro min-h-10" onClick={aoFechar}>Fechar</button>
      </div>
      <div role="radiogroup" aria-label="Tipo de despesa" className="flex flex-wrap gap-2">
        {TIPOS_DESPESA.map((t) => (
          <button
            key={t.id}
            type="button"
            role="radio"
            aria-checked={tipo === t.id}
            onClick={() => setTipo(t.id)}
            className={`flex flex-col items-start rounded-xl border px-3.5 py-2 text-left ${tipo === t.id ? "border-verde bg-verde text-white" : "border-borda bg-white text-ink"}`}
          >
            <span className="text-sm font-bold">{t.nome}</span>
            <span className={`text-xs ${tipo === t.id ? "text-menta" : "text-muted"}`}>{t.exemplo}</span>
          </button>
        ))}
      </div>
      <FormLancamento
        key={tipo}
        tipo={tipo}
        mes={mes}
        editando={null}
        categorias={categorias.filter((c) => c.tipo === tipo)}
        aoCriarCategoria={aoCriarCategoria}
        aoSalvar={() => aoSalvar(tipo)}
        aoCancelar={aoFechar}
      />
    </section>
  );
}

/* ---------- lista e formulário de lançamentos ---------- */

function Lancamentos({ tipo, mes, itens, categorias, aoCriarCategoria, aoMudar }: {
  tipo: TipoLancamento;
  mes: string;
  itens: Lancamento[];
  categorias: Categoria[];
  aoCriarCategoria: (c: Categoria) => void;
  aoMudar: () => void;
}) {
  const [editando, setEditando] = useState<Lancamento | null>(null);
  const [erro, setErro] = useState<string | null>(null);
  const total = itens.reduce((t, l) => t + Math.round(l.valor * 100), 0) / 100;

  async function excluir(l: Lancamento) {
    if (!confirm(`Excluir "${l.descricao || l.categoriaNome}" de ${reais(l.valor)}?`)) return;
    try {
      await excluirLancamento(l.id);
      if (editando?.id === l.id) setEditando(null);
      aoMudar();
    } catch (e) {
      setErro((e as Error).message);
    }
  }

  return (
    <div className="flex flex-col gap-4">
      <FormLancamento
        key={editando?.id ?? "novo"}
        tipo={tipo}
        mes={mes}
        editando={editando}
        categorias={categorias}
        aoCriarCategoria={aoCriarCategoria}
        aoSalvar={() => {
          setEditando(null);
          aoMudar();
        }}
        aoCancelar={() => setEditando(null)}
      />
      <Erro texto={erro} />
      {itens.length === 0 ? (
        <p className="m-0 py-4 text-muted">Nenhum lançamento em {NOMES_LANCAMENTO[tipo].toLowerCase()} neste mês.</p>
      ) : (
        <ul className="m-0 flex list-none flex-col p-0">
          {itens.map((l) => (
            <li key={l.id} className="flex flex-wrap items-center gap-x-4 gap-y-2 border-b border-linha px-1 py-3 last:border-0">
              <span className="w-12 font-bold">{formatar(l.data)}</span>
              <span className="flex min-w-0 flex-[1_1_200px] flex-col leading-tight">
                <span className="font-bold">{l.categoriaNome}</span>
                {l.descricao && <span className="text-sm text-muted">{l.descricao}</span>}
              </span>
              {l.recorrente && <span className="chip bg-[#dce7fb] text-[#1e3a8a]">Repete todo mês</span>}
              <span className="w-28 text-right font-extrabold">{reais(l.valor)}</span>
              <span className="flex gap-2">
                <button type="button" className="btn btn-claro min-h-10" onClick={() => setEditando(l)}>Editar</button>
                <button type="button" className="btn btn-claro min-h-10" onClick={() => excluir(l)}>Excluir</button>
              </span>
            </li>
          ))}
          <li className="flex justify-end gap-4 px-1 pt-3 font-extrabold">
            <span>Total</span>
            <span className="w-28 text-right">{reais(total)}</span>
            <span className="w-[178px]" aria-hidden />
          </li>
        </ul>
      )}
    </div>
  );
}

const NOVA = -1;

function FormLancamento({ tipo, mes, editando, categorias, aoCriarCategoria, aoSalvar, aoCancelar }: {
  tipo: TipoLancamento;
  mes: string;
  editando: Lancamento | null;
  categorias: Categoria[];
  aoCriarCategoria: (c: Categoria) => void;
  aoSalvar: () => void;
  aoCancelar: () => void;
}) {
  const diaPadrao = hoje().startsWith(mes) ? hoje() : `${mes}-01`;
  const [data, setData] = useState(editando?.data ?? diaPadrao);
  const [categoriaId, setCategoriaId] = useState(editando?.categoriaId ?? 0);
  const [novaCategoria, setNovaCategoria] = useState("");
  const [descricao, setDescricao] = useState(editando?.descricao ?? "");
  const [valor, setValor] = useState(editando ? editando.valor.toFixed(2).replace(".", ",") : "");
  const [recorrente, setRecorrente] = useState(editando?.recorrente ?? tipo === "FIXO");
  const [erro, setErro] = useState<string | null>(null);
  const [salvando, setSalvando] = useState(false);

  async function salvar(e: React.FormEvent) {
    e.preventDefault();
    const v = lerValor(valor);
    if (v === null || v <= 0) return setErro("Informe um valor maior que zero, com até 2 casas (ex.: 150,90).");
    if (categoriaId === 0) return setErro("Escolha a categoria.");
    if (categoriaId === NOVA && !novaCategoria.trim()) return setErro("Digite o nome da nova categoria.");
    setSalvando(true);
    setErro(null);
    try {
      let id = categoriaId;
      if (id === NOVA) {
        const c = await criarCategoria(novaCategoria, tipo);
        aoCriarCategoria(c);
        id = c.id;
      }
      const dados: DadosLancamento = { data, categoriaId: id, descricao, valor: v, recorrente: podeRepetir(tipo) && recorrente };
      if (editando) await alterarLancamento(editando.id, dados);
      else await criarLancamento(dados);
      aoSalvar();
      if (!editando) {
        setValor("");
        setDescricao("");
      }
    } catch (er) {
      setErro((er as Error).message);
    } finally {
      setSalvando(false);
    }
  }

  return (
    <form onSubmit={salvar} className="grid grid-cols-[repeat(auto-fit,minmax(160px,1fr))] items-end gap-3.5 rounded-2xl bg-fundo p-4">
      <p className="col-span-full m-0 font-bold">{editando ? "Editar lançamento" : `Novo lançamento em ${NOMES_LANCAMENTO[tipo].toLowerCase()}`}</p>
      <label className="flex flex-col gap-1.5">
        <span className="rotulo">Data</span>
        <input className="campo" type="date" required value={data} onChange={(e) => setData(e.target.value)} />
      </label>
      <label className="flex flex-col gap-1.5">
        <span className="rotulo">Categoria</span>
        <select className="campo" value={categoriaId} onChange={(e) => setCategoriaId(Number(e.target.value))}>
          <option value={0}>Selecione</option>
          {categorias.map((c) => (
            <option key={c.id} value={c.id}>{c.nome}</option>
          ))}
          <option value={NOVA}>+ Nova categoria…</option>
        </select>
      </label>
      {categoriaId === NOVA && (
        <label className="flex flex-col gap-1.5">
          <span className="rotulo">Nome da nova categoria</span>
          <input className="campo" maxLength={60} value={novaCategoria} onChange={(e) => setNovaCategoria(e.target.value)} />
        </label>
      )}
      <label className="flex flex-col gap-1.5">
        <span className="rotulo">Descrição (opcional)</span>
        <input className="campo" maxLength={200} placeholder="Ex.: conta de luz" value={descricao} onChange={(e) => setDescricao(e.target.value)} />
      </label>
      <label className="flex flex-col gap-1.5">
        <span className="rotulo">Valor (R$)</span>
        <input className="campo" inputMode="decimal" placeholder="0,00" value={valor} onChange={(e) => setValor(e.target.value)} />
      </label>
      {podeRepetir(tipo) && (
        <label className="flex min-h-[46px] items-center gap-2 text-[15px] font-semibold">
          <input type="checkbox" className="size-5 accent-verde" checked={recorrente} onChange={(e) => setRecorrente(e.target.checked)} />
          Repete todo mês
        </label>
      )}
      <div className="flex gap-2">
        <button type="submit" disabled={salvando} className="btn btn-verde flex-1">{salvando ? "Salvando…" : "Salvar"}</button>
        {editando && <button type="button" className="btn btn-claro" onClick={aoCancelar}>Cancelar</button>}
      </div>
      <div className="col-span-full empty:hidden"><Erro texto={erro} /></div>
    </form>
  );
}

/* ---------- receitas de atendimento ---------- */

function Atendimentos({ itens }: { itens: Agendamento[] }) {
  const total = itens.reduce((t, a) => t + Math.round((a.valor ?? 0) * 100), 0) / 100;
  return (
    <div className="flex flex-col gap-2 border-t border-linha pt-4">
      <h2 className="m-0 text-lg font-extrabold">Atendimentos concluídos ({itens.length})</h2>
      <p className="m-0 text-sm text-muted">Entram sozinhos quando o atendimento é concluído na agenda. O valor muda lá.</p>
      {itens.length === 0 ? (
        <p className="m-0 py-4 text-muted">Nenhum atendimento concluído neste mês.</p>
      ) : (
        <ul className="m-0 flex max-h-96 list-none flex-col overflow-y-auto p-0">
          {itens.map((a) => (
            <li key={a.id} className="flex flex-wrap items-center gap-x-4 border-b border-linha px-1 py-2.5 last:border-0">
              <span className="w-12 font-bold">{formatar(a.data)}</span>
              <span className="min-w-0 flex-[1_1_200px]">
                {NOMES_SERVICO[a.tipo]} <span className="text-muted">· {a.petNome} ({a.tutorNome})</span>
              </span>
              <span className="w-28 text-right font-bold">{a.valor === null ? <span className="text-muted">sem valor</span> : reais(a.valor)}</span>
            </li>
          ))}
        </ul>
      )}
      <p className="m-0 flex justify-end gap-4 px-1 font-extrabold">
        <span>Total de atendimentos</span>
        <span className="w-28 text-right">{reais(total)}</span>
      </p>
    </div>
  );
}

/* ---------- preço padrão dos serviços ---------- */

function Precos() {
  const [precos, setPrecos] = useState<PrecoServico[] | null>(null);
  const [textos, setTextos] = useState<Record<string, string>>({});
  const [aviso, setAviso] = useState<string | null>(null);
  const [erro, setErro] = useState<string | null>(null);

  useEffect(() => {
    listarPrecos()
      .then((p) => {
        setPrecos(p);
        setTextos(Object.fromEntries(p.map((x) => [x.tipo, x.valor === null ? "" : x.valor.toFixed(2).replace(".", ",")])));
      })
      .catch((e: Error) => setErro(e.message));
  }, []);

  async function salvar(e: React.FormEvent) {
    e.preventDefault();
    setErro(null);
    setAviso(null);
    try {
      for (const p of precos!) {
        const txt = textos[p.tipo] ?? "";
        if (!txt.trim()) continue;
        const v = lerValor(txt);
        if (v === null) throw new Error(`Valor inválido em ${NOMES_SERVICO[p.tipo]}.`);
        if (v !== p.valor) await definirPreco(p.tipo, v);
      }
      setPrecos(await listarPrecos());
      setAviso("Preços salvos.");
    } catch (er) {
      setErro((er as Error).message);
    }
  }

  if (!precos) return erro ? <Erro texto={erro} /> : <Carregando />;
  return (
    <form onSubmit={salvar} className="flex flex-col gap-4">
      <p className="m-0 text-muted">Ao concluir um atendimento na agenda, o valor vem preenchido com o preço do serviço. Dá para mudar na hora.</p>
      <div className="grid grid-cols-[repeat(auto-fit,minmax(170px,1fr))] gap-3.5">
        {precos.map((p) => (
          <label key={p.tipo} className="flex flex-col gap-1.5">
            <span className="rotulo">{NOMES_SERVICO[p.tipo]} (R$)</span>
            <input className="campo" inputMode="decimal" placeholder="0,00" value={textos[p.tipo] ?? ""}
              onChange={(e) => setTextos({ ...textos, [p.tipo]: e.target.value })} />
          </label>
        ))}
      </div>
      <div className="flex flex-wrap items-center gap-3">
        <button type="submit" className="btn btn-verde">Salvar preços</button>
        {aviso && <span role="status" className="font-semibold text-verde">{aviso}</span>}
      </div>
      <Erro texto={erro} />
    </form>
  );
}

/* ---------- histórico de alterações (só leitura) ---------- */

const NOMES_ACAO: Record<RegistroHistorico["acao"], string> = {
  CRIOU: "Criou", ALTEROU: "Alterou", EXCLUIU: "Excluiu", COPIOU: "Lançou sozinho (recorrente)",
};

function Historico({ versao }: { versao: number }) {
  const [aberto, setAberto] = useState(false);
  const [itens, setItens] = useState<RegistroHistorico[] | null>(null);
  const [erro, setErro] = useState<string | null>(null);

  useEffect(() => {
    if (!aberto) return;
    listarHistorico().then(setItens).catch((e: Error) => setErro(e.message));
  }, [aberto, versao]);

  return (
    <details className="cartao" onToggle={(e) => setAberto(e.currentTarget.open)}>
      <summary className="cursor-pointer font-extrabold">Últimas alterações no financeiro</summary>
      <p className="mt-2 mb-3 text-sm text-muted">
        Tudo o que foi criado, alterado ou excluído no financeiro fica registrado aqui com usuário, data e hora. Este histórico não pode ser editado nem apagado.
      </p>
      <Erro texto={erro} />
      {!itens ? (
        <Carregando />
      ) : itens.length === 0 ? (
        <p className="m-0 text-muted">Nenhuma alteração ainda.</p>
      ) : (
        <ul className="m-0 flex max-h-96 list-none flex-col overflow-y-auto p-0 text-sm">
          {itens.map((h, i) => (
            <li key={i} className="flex flex-col gap-0.5 border-b border-linha py-2.5 last:border-0">
              <span className="font-bold">
                {new Date(h.em).toLocaleString("pt-BR", { timeZone: "America/Sao_Paulo", dateStyle: "short", timeStyle: "short" })} · {h.usuario} · {NOMES_ACAO[h.acao]} {h.entidade} #{h.entidadeId}
              </span>
              {h.antes && <span className="text-muted">Antes: {h.antes}</span>}
              {h.depois && <span>Depois: {h.depois}</span>}
            </li>
          ))}
        </ul>
      )}
    </details>
  );
}

/* ---------- categorias: renomear e excluir (só sem lançamentos) ---------- */

function Categorias({ categorias, aoMudar }: { categorias: Categoria[]; aoMudar: () => Promise<void> }) {
  const [editandoId, setEditandoId] = useState<number | null>(null);
  const [nome, setNome] = useState("");
  const [erro, setErro] = useState<string | null>(null);

  async function salvar(e: React.FormEvent, id: number) {
    e.preventDefault();
    if (!nome.trim()) return setErro("Informe o nome.");
    try {
      await renomearCategoria(id, nome);
      setEditandoId(null);
      setErro(null);
      await aoMudar();
    } catch (er) {
      setErro((er as Error).message);
    }
  }

  return (
    <div className="flex flex-col gap-4">
      <p className="m-0 text-muted">Renomeie categorias (o nome muda em todos os lançamentos) ou exclua as que não têm lançamentos.</p>
      <Erro texto={erro} />
      <div className="grid grid-cols-[repeat(auto-fit,minmax(260px,1fr))] gap-4">
        {(Object.keys(NOMES_LANCAMENTO) as TipoLancamento[]).map((tipo) => (
          <section key={tipo} aria-label={NOMES_LANCAMENTO[tipo]} className="flex flex-col gap-1 rounded-2xl bg-fundo p-4">
            <h3 className="m-0 mb-1 text-base font-extrabold">{NOMES_LANCAMENTO[tipo]}</h3>
            {categorias.filter((c) => c.tipo === tipo).map((c) =>
              editandoId === c.id ? (
                <form key={c.id} onSubmit={(e) => salvar(e, c.id)} className="flex flex-wrap items-center gap-2 py-1">
                  <input className="campo min-h-10 flex-1" maxLength={60} value={nome} onChange={(e) => setNome(e.target.value)} aria-label={`Novo nome de ${c.nome}`} />
                  <button type="submit" className="btn btn-verde min-h-10">Salvar</button>
                  <button type="button" className="btn btn-claro min-h-10" onClick={() => setEditandoId(null)}>Cancelar</button>
                </form>
              ) : (
                <div key={c.id} className="flex flex-wrap items-center justify-between gap-2 border-b border-linha py-1.5 last:border-0">
                  <span className="font-semibold">{c.nome}</span>
                  <span className="flex flex-wrap gap-2">
                    <button type="button" className="btn btn-claro min-h-9 px-3" onClick={() => { setEditandoId(c.id); setNome(c.nome); setErro(null); }}>
                      Renomear
                    </button>
                    <BotaoExcluir
                      pergunta={`Excluir "${c.nome}"?`}
                      aoConfirmar={async () => {
                        try {
                          await excluirCategoria(c.id);
                          setErro(null);
                          await aoMudar();
                        } catch (er) {
                          setErro((er as Error).message);
                        }
                      }}
                    />
                  </span>
                </div>
              ),
            )}
          </section>
        ))}
      </div>
    </div>
  );
}
