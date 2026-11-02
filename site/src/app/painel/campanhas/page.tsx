"use client";

import { useCallback, useEffect, useState } from "react";
import { Carregando, Erro, Titulo } from "@/components/Ui";
import { criarCampanha, listarCampanhas, publicoCampanha } from "@/lib/api";
import type { Campanha, StatusEnvio } from "@/lib/tipos";

const NOMES: Record<StatusEnvio, string> = {
  ENVIADA: "enviadas", SIMULADA: "simuladas", PENDENTE: "para amanhã", FALHOU: "com falha", PULADO: "saíram da lista",
};

/** Campanhas de promoção: só para clientes que aceitaram receber (LGPD), com link de saída em toda mensagem. */
export default function Campanhas() {
  const [lista, setLista] = useState<Campanha[] | null>(null);
  const [publico, setPublico] = useState<number | null>(null);
  const [titulo, setTitulo] = useState("");
  const [texto, setTexto] = useState("");
  const [template, setTemplate] = useState("promocao_loja");
  const [erro, setErro] = useState<string | null>(null);
  const [aviso, setAviso] = useState<string | null>(null);
  const [enviando, setEnviando] = useState(false);

  const carregar = useCallback(async () => {
    try {
      const [l, p] = await Promise.all([listarCampanhas(), publicoCampanha()]);
      setLista(l);
      setPublico(p);
    } catch (e) {
      setErro((e as Error).message);
    }
  }, []);

  useEffect(() => {
    carregar();
  }, [carregar]);

  async function enviar(e: React.FormEvent) {
    e.preventDefault();
    setEnviando(true);
    setErro(null);
    setAviso(null);
    try {
      const c = await criarCampanha(titulo, texto, template);
      setAviso(c.envios.PENDENTE > 0
        ? `Campanha iniciada. ${c.envios.PENDENTE} mensagens ficam para amanhã por causa do limite diário.`
        : "Campanha enviada.");
      setTitulo("");
      setTexto("");
      await carregar();
    } catch (er) {
      setErro((er as Error).message);
    } finally {
      setEnviando(false);
    }
  }

  return (
    <>
      <Titulo sobre="Painel da loja" titulo="Campanhas" />
      <section className="cartao flex flex-col gap-2 bg-[#fdf3e1]">
        <h2 className="m-0 text-lg font-extrabold">Só para quem aceitou receber promoções</h2>
        <p className="m-0 text-[15px]">
          Pela LGPD e pelas regras do WhatsApp, campanha vai <b>somente</b> para clientes da loja que aceitaram (no agendamento online ou
          pedindo na loja). Toda mensagem leva o link para sair da lista. Para alcançar gente nova, use Google, Instagram e anúncios,
          nunca listas de números compradas (o número da loja é banido).
        </p>
      </section>

      <form onSubmit={enviar} className="cartao flex flex-col gap-3.5">
        <h2 className="m-0 text-xl font-extrabold">Nova campanha</h2>
        <p className="m-0 font-semibold">
          {publico === null ? "Contando clientes…" : `Vai para ${publico} cliente(s) que aceitaram promoções.`}
        </p>
        <label className="flex flex-col gap-1.5">
          <span className="rotulo">Título (só para você)</span>
          <input className="campo" required maxLength={80} placeholder="Ex.: Banho com hidratação grátis" value={titulo} onChange={(e) => setTitulo(e.target.value)} />
        </label>
        <label className="flex flex-col gap-1.5">
          <span className="rotulo">Mensagem</span>
          <textarea className="campo min-h-28 py-2.5" required maxLength={500} value={texto} onChange={(e) => setTexto(e.target.value)}
            placeholder="Ex.: Nesta semana, todo banho sai com hidratação grátis! Agende pelo link do nosso site." />
        </label>
        <label className="flex max-w-[360px] flex-col gap-1.5">
          <span className="rotulo">Modelo de marketing aprovado na Meta</span>
          <input className="campo" required pattern="[a-z0-9_]{1,80}" value={template} onChange={(e) => setTemplate(e.target.value)} />
        </label>
        <Erro texto={erro} />
        {aviso && <p role="status" className="m-0 rounded-xl bg-[#ddf2ea] px-4 py-3 font-semibold text-[#0b5e4f]">{aviso}</p>}
        <button type="submit" disabled={enviando || !publico} className="btn btn-verde self-start">
          {enviando ? "Enviando…" : "Enviar campanha"}
        </button>
      </form>

      <section aria-label="Campanhas enviadas" className="cartao flex flex-col gap-3">
        <h2 className="m-0 text-xl font-extrabold">Relatório</h2>
        {!lista ? (
          <Carregando />
        ) : lista.length === 0 ? (
          <p className="m-0 text-muted">Nenhuma campanha ainda.</p>
        ) : (
          <ul className="m-0 flex list-none flex-col p-0">
            {lista.map((c) => (
              <li key={c.id} className="flex flex-col gap-1 border-b border-linha py-3 last:border-0">
                <span className="font-bold">
                  {c.titulo} · <span className="font-medium text-muted">{new Date(c.criadaEm).toLocaleDateString("pt-BR", { timeZone: "America/Sao_Paulo" })}</span>{" "}
                  <span className={`chip ${c.status === "CONCLUIDA" ? "bg-[#ddf2ea] text-[#0b5e4f]" : "bg-[#fdecc8] text-[#7a4a00]"}`}>
                    {c.status === "CONCLUIDA" ? "Concluída" : "Enviando"}
                  </span>
                </span>
                <span className="text-sm text-muted">{c.texto}</span>
                <span className="text-sm font-semibold">
                  {(Object.keys(NOMES) as StatusEnvio[]).filter((k) => c.envios[k] > 0).map((k) => `${c.envios[k]} ${NOMES[k]}`).join(" · ")}
                </span>
              </li>
            ))}
          </ul>
        )}
      </section>
    </>
  );
}
