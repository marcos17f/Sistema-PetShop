"use client";

import { useEffect, useState } from "react";
import { Carregando, Erro, Titulo } from "@/components/Ui";
import { buscarConfig, salvarConfig } from "@/lib/api";
import type { ConfigLoja } from "@/lib/tipos";

/** Tela para o dono mudar os dados da loja sem programador. O site público passa a mostrar em até 1 minuto. */
export default function Configuracoes() {
  const [c, setC] = useState<ConfigLoja | null>(null);
  const [erro, setErro] = useState<string | null>(null);
  const [aviso, setAviso] = useState<string | null>(null);
  const [salvando, setSalvando] = useState(false);

  useEffect(() => {
    buscarConfig().then(setC).catch((e: Error) => setErro(e.message));
  }, []);

  if (!c) return erro ? <Erro texto={erro} /> : <Carregando />;
  const muda = (campo: keyof ConfigLoja, valor: string | number | null) => setC({ ...c, [campo]: valor });
  const sabadoAberto = c.sabadoAbre !== null;

  async function salvar(e: React.FormEvent) {
    e.preventDefault();
    if (!c) return;
    setSalvando(true);
    setErro(null);
    setAviso(null);
    try {
      setC(await salvarConfig(c));
      setAviso("Configurações salvas. O site mostra os dados novos em até 1 minuto.");
    } catch (er) {
      setErro((er as Error).message);
    } finally {
      setSalvando(false);
    }
  }

  const campo = (rotulo: string, chave: keyof ConfigLoja, props: React.InputHTMLAttributes<HTMLInputElement> = {}) => (
    <label className="flex flex-col gap-1.5">
      <span className="rotulo">{rotulo}</span>
      <input className="campo" value={(c[chave] as string | number | null) ?? ""} onChange={(e) => muda(chave, e.target.value)} {...props} />
    </label>
  );

  return (
    <form onSubmit={salvar} className="flex flex-col gap-6">
      <Titulo sobre="Painel da loja" titulo="Configurações">
        <button type="submit" disabled={salvando} className="btn btn-verde">{salvando ? "Salvando…" : "Salvar configurações"}</button>
      </Titulo>
      <Erro texto={erro} />
      {aviso && <p role="status" className="m-0 rounded-xl bg-[#ddf2ea] px-4 py-3 font-semibold text-[#0b5e4f]">{aviso}</p>}

      <section aria-label="Loja" className="cartao flex flex-col gap-4">
        <h2 className="m-0 text-xl font-extrabold">Loja</h2>
        <p className="m-0 text-sm text-muted">Aparece no site, nos botões de WhatsApp e no Excel do financeiro.</p>
        <div className="grid grid-cols-[repeat(auto-fit,minmax(220px,1fr))] gap-3.5">
          {campo("Nome da loja", "nome", { maxLength: 120, required: true })}
          {campo("WhatsApp da loja (com DDD)", "whatsapp", { type: "tel", required: true, placeholder: "(62) 99999-0000" })}
          {campo("Cidade", "cidade", { maxLength: 80, placeholder: "Goiânia – GO" })}
          {campo("Endereço", "endereco", { maxLength: 200 })}
          {campo("Horário (texto do site)", "horarioTexto", { maxLength: 120, placeholder: "Seg a sex, 8h às 18h · Sáb, 8h às 12h" })}
        </div>
      </section>

      <section aria-label="Agenda" className="cartao flex flex-col gap-4">
        <h2 className="m-0 text-xl font-extrabold">Agenda</h2>
        <p className="m-0 text-sm text-muted">
          O intervalo vale para qualquer cliente: com 40 min e um banho às 9h, o próximo só pode ser a partir de 9h40.
          O horário de funcionamento define os horários livres do agendamento online. Domingo fica fechado.
        </p>
        <div className="grid grid-cols-[repeat(auto-fit,minmax(170px,1fr))] items-end gap-3.5">
          <label className="flex flex-col gap-1.5">
            <span className="rotulo">Intervalo entre atendimentos (min)</span>
            <input className="campo" type="number" min={10} max={240} step={5} value={c.intervaloAgendaMinutos}
              onFocus={(e) => e.target.select()} onChange={(e) => muda("intervaloAgendaMinutos", Number(e.target.value))} />
          </label>
          {campo("Seg a sex: abre", "abre", { type: "time", required: true })}
          {campo("Seg a sex: fecha", "fecha", { type: "time", required: true })}
          <label className="flex min-h-[46px] items-center gap-2 text-[15px] font-semibold">
            <input type="checkbox" className="size-5 accent-verde" checked={sabadoAberto}
              onChange={(e) => setC({ ...c, sabadoAbre: e.target.checked ? "08:00" : null, sabadoFecha: e.target.checked ? "12:00" : null })} />
            Abre no sábado
          </label>
          {sabadoAberto && campo("Sábado: abre", "sabadoAbre", { type: "time", required: true })}
          {sabadoAberto && campo("Sábado: fecha", "sabadoFecha", { type: "time", required: true })}
        </div>
      </section>

      <section aria-label="Divulgação" className="cartao flex flex-col gap-4">
        <h2 className="m-0 text-xl font-extrabold">Divulgação</h2>
        <p className="m-0 text-sm text-muted">
          Instagram aparece no site. O link de avaliação vira o botão “Avalie no Google” (no Perfil da Empresa no Google:
          <i> Pedir avaliações</i> → copiar o link).
        </p>
        <div className="grid grid-cols-[repeat(auto-fit,minmax(260px,1fr))] gap-3.5">
          {campo("Instagram (só o usuário, sem @)", "instagram", { maxLength: 30, placeholder: "petshopdocleiton" })}
          {campo("Link de avaliação do Google", "linkAvaliacao", { type: "url", maxLength: 300, placeholder: "https://g.page/r/..." })}
        </div>
      </section>
    </form>
  );
}
