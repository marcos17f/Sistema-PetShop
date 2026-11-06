"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { Marca } from "@/components/Pata";
import { Erro } from "@/components/Ui";
import { agendarOnline, buscarLojaPublica, horariosLivres, listarServicosPublicos } from "@/lib/api";
import { diaPorExtenso, hoje, somarDias } from "@/lib/datas";
import { linkWhatsapp } from "@/lib/loja";
import { reais, type ConfigLoja, type ConfirmacaoOnline, type PedidoOnline, type ServicoPublico, type TipoServico } from "@/lib/tipos";

const VAZIO: PedidoOnline = {
  nome: "", whatsapp: "", petNome: "", especie: "Cão", tipo: "BANHO", data: "", hora: "", observacao: "",
  buscaMoto: false, enderecoBusca: "", aceiteDados: false, aceitePromocoes: false, origem: "", site: "",
};

/** Página pública: o cliente escolhe serviço, dia e horário livre, e informa os dados. Sem login. */
export function AgendarOnline() {
  const [servicos, setServicos] = useState<ServicoPublico[]>([]);
  const [p, setP] = useState<PedidoOnline>(VAZIO);
  const [horas, setHoras] = useState<string[] | null>(null);
  const [erro, setErro] = useState<string | null>(null);
  const [enviando, setEnviando] = useState(false);
  const [feito, setFeito] = useState<ConfirmacaoOnline | null>(null);
  const [lojaInfo, setLojaInfo] = useState<ConfigLoja | null>(null);

  useEffect(() => {
    listarServicosPublicos().then(setServicos).catch((e: Error) => setErro(e.message));
    buscarLojaPublica().then(setLojaInfo).catch(() => undefined);
    // origem do link (ex.: ?origem=instagram na bio) para o cadastro do cliente
    const origem = new URLSearchParams(window.location.search).get("origem") ?? "";
    setP((x) => ({ ...x, origem: origem.slice(0, 30) }));
  }, []);

  useEffect(() => {
    if (!p.data) return;
    setHoras(null);
    horariosLivres(p.data).then(setHoras).catch((e: Error) => setErro(e.message));
  }, [p.data]);

  async function enviar(e: React.FormEvent) {
    e.preventDefault();
    if (!p.hora) return setErro("Escolha um horário livre.");
    setEnviando(true);
    setErro(null);
    try {
      setFeito(await agendarOnline(p));
    } catch (er) {
      setErro((er as Error).message);
      if (p.data) horariosLivres(p.data).then(setHoras).catch(() => undefined); // horário pode ter acabado de ser ocupado
    } finally {
      setEnviando(false);
    }
  }

  const mudar = <K extends keyof PedidoOnline>(k: K, v: PedidoOnline[K]) => setP({ ...p, [k]: v });

  return (
    <div className="min-h-screen bg-fundo">
      <header className="bg-verde-escuro text-menta">
        <div className="mx-auto flex max-w-[760px] items-center justify-between gap-4 px-5 py-4">
          <Link href="/" className="text-menta no-underline"><Marca nome={lojaInfo?.nome} subtitulo="Agendamento online" /></Link>
        </div>
      </header>

      <main className="mx-auto flex max-w-[760px] flex-col gap-5 px-5 py-8">
        {feito ? (
          <section className="cartao flex flex-col gap-3" role="status">
            <h1 className="m-0 text-[28px] font-extrabold">Agendamento recebido!</h1>
            <p className="m-0 text-lg">
              <b>{feito.servico.charAt(0).toUpperCase() + feito.servico.slice(1)}</b> do <b>{feito.petNome}</b> em <b>{feito.data}</b> às <b>{feito.hora}</b>
              {feito.buscaMoto && " · com busca de moto"}.
            </p>
            <p className="m-0 text-muted">A loja vai confirmar pelo WhatsApp. Se precisar mudar algo, é só chamar.</p>
            <div className="flex flex-wrap gap-2">
              {lojaInfo?.whatsapp && (
                <a className="btn btn-verde" target="_blank" rel="noopener noreferrer"
                  href={linkWhatsapp(lojaInfo.whatsapp, `Olá! Acabei de agendar online: ${feito.servico} do ${feito.petNome} em ${feito.data} às ${feito.hora}.`)}>
                  Falar com a loja no WhatsApp
                </a>
              )}
              <Link href="/" className="btn btn-claro">Voltar ao site</Link>
            </div>
          </section>
        ) : (
          <form onSubmit={enviar} className="flex flex-col gap-5">
            <h1 className="m-0 text-[30px] font-extrabold tracking-tight">Agende o banho ou a tosa do seu pet</h1>

            <section aria-label="Serviço" className="cartao flex flex-col gap-3">
              <h2 className="m-0 text-lg font-extrabold">1. Serviço</h2>
              <div className="grid grid-cols-[repeat(auto-fit,minmax(150px,1fr))] gap-2.5" role="radiogroup" aria-label="Serviço">
                {servicos.map((s) => (
                  <button key={s.tipo} type="button" role="radio" aria-checked={p.tipo === s.tipo} onClick={() => mudar("tipo", s.tipo as TipoServico)}
                    className={`flex flex-col items-start rounded-2xl border-2 px-4 py-3 text-left ${p.tipo === s.tipo ? "border-verde bg-[#e8eefa]" : "border-borda bg-white"}`}>
                    <span className="font-extrabold">{s.nome}</span>
                    {s.preco !== null && <span className="text-sm text-muted">a partir de {reais(s.preco)}</span>}
                  </button>
                ))}
              </div>
            </section>

            <section aria-label="Dia e horário" className="cartao flex flex-col gap-3">
              <h2 className="m-0 text-lg font-extrabold">2. Dia e horário</h2>
              <label className="flex max-w-[260px] flex-col gap-1.5">
                <span className="rotulo">Dia</span>
                <input className="campo" type="date" required min={hoje()} max={somarDias(hoje(), 60)} value={p.data}
                  onChange={(e) => setP({ ...p, data: e.target.value, hora: "" })} />
              </label>
              {p.data && (
                <>
                  <p className="m-0 text-sm font-semibold text-muted">{diaPorExtenso(p.data)}</p>
                  {!horas ? (
                    <p className="m-0 text-muted" role="status">Buscando horários…</p>
                  ) : horas.length === 0 ? (
                    <p className="m-0 text-muted">Sem horários livres neste dia (a loja não abre aos domingos). Escolha outro dia.</p>
                  ) : (
                    <div className="flex flex-wrap gap-2" role="radiogroup" aria-label="Horários livres">
                      {horas.map((h) => (
                        <button key={h} type="button" role="radio" aria-checked={p.hora === h} onClick={() => mudar("hora", h)}
                          className={`min-h-11 rounded-xl border-2 px-4 font-bold ${p.hora === h ? "border-verde bg-verde text-white" : "border-borda bg-white text-ink"}`}>
                          {h}
                        </button>
                      ))}
                    </div>
                  )}
                </>
              )}
            </section>

            <section aria-label="Seus dados" className="cartao grid grid-cols-[repeat(auto-fit,minmax(220px,1fr))] gap-3.5">
              <h2 className="col-span-full m-0 text-lg font-extrabold">3. Seus dados e do pet</h2>
              <label className="flex flex-col gap-1.5"><span className="rotulo">Seu nome</span>
                <input className="campo" required maxLength={120} autoComplete="name" value={p.nome} onChange={(e) => mudar("nome", e.target.value)} /></label>
              <label className="flex flex-col gap-1.5"><span className="rotulo">WhatsApp com DDD</span>
                <input className="campo" required type="tel" autoComplete="tel" placeholder="(62) 99999-0000" value={p.whatsapp} onChange={(e) => mudar("whatsapp", e.target.value)} /></label>
              <label className="flex flex-col gap-1.5"><span className="rotulo">Nome do pet</span>
                <input className="campo" required maxLength={80} value={p.petNome} onChange={(e) => mudar("petNome", e.target.value)} /></label>
              <label className="flex flex-col gap-1.5"><span className="rotulo">Espécie</span>
                <select className="campo" value={p.especie} onChange={(e) => mudar("especie", e.target.value as PedidoOnline["especie"])}>
                  <option>Cão</option><option>Gato</option><option>Outro</option>
                </select></label>
              <label className="col-span-full flex flex-col gap-1.5"><span className="rotulo">Observação (opcional)</span>
                <input className="campo" maxLength={200} placeholder="Ex.: tem medo de secador" value={p.observacao} onChange={(e) => mudar("observacao", e.target.value)} /></label>
              <label className="col-span-full flex items-center gap-2 text-[15px] font-semibold">
                <input type="checkbox" className="size-5 accent-verde" checked={p.buscaMoto} onChange={(e) => mudar("buscaMoto", e.target.checked)} />
                Quero que a loja busque meu pet de moto
              </label>
              {p.buscaMoto && (
                <label className="col-span-full flex flex-col gap-1.5"><span className="rotulo">Endereço para a busca</span>
                  <input className="campo" required maxLength={200} autoComplete="street-address" value={p.enderecoBusca} onChange={(e) => mudar("enderecoBusca", e.target.value)} /></label>
              )}
              {/* contra robôs: escondido de pessoas e de leitores de tela */}
              <div aria-hidden="true" className="absolute -left-[9999px] h-0 w-0 overflow-hidden">
                <label>Site<input tabIndex={-1} autoComplete="off" value={p.site} onChange={(e) => mudar("site", e.target.value)} /></label>
              </div>
            </section>

            <section aria-label="Privacidade" className="cartao flex flex-col gap-2.5 text-[15px]">
              <label className="flex items-start gap-2.5">
                <input type="checkbox" required className="mt-0.5 size-5 shrink-0 accent-verde" checked={p.aceiteDados} onChange={(e) => mudar("aceiteDados", e.target.checked)} />
                <span>Autorizo a loja a usar meu nome, WhatsApp e os dados do meu pet para este agendamento e para os lembretes de banho e vacina (LGPD).</span>
              </label>
              <label className="flex items-start gap-2.5">
                <input type="checkbox" className="mt-0.5 size-5 shrink-0 accent-verde" checked={p.aceitePromocoes} onChange={(e) => mudar("aceitePromocoes", e.target.checked)} />
                <span>Quero receber promoções da loja pelo WhatsApp (opcional; dá para sair quando quiser).</span>
              </label>
            </section>

            <Erro texto={erro} />
            <button type="submit" disabled={enviando || !p.hora} className="btn btn-verde min-h-13 text-lg">
              {enviando ? "Agendando…" : p.hora ? `Agendar para ${p.hora}` : "Escolha um horário"}
            </button>
          </form>
        )}
      </main>
    </div>
  );
}
