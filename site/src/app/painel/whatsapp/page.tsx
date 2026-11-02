"use client";

import { useCallback, useEffect, useState } from "react";
import { Carregando, Erro, Titulo } from "@/components/Ui";
import { buscarWhatsapp, enviarTesteWhatsapp, lembretesAgora, listarMensagens, salvarWhatsapp } from "@/lib/api";
import { linkWhatsapp } from "@/lib/loja";
import { NOMES_MENSAGEM, type MensagemWhatsApp, type SituacaoWhatsApp } from "@/lib/tipos";

const COR_STATUS: Record<MensagemWhatsApp["status"], string> = {
  SIMULADA: "bg-[#fdecc8] text-[#7a4a00]",
  ENVIADA: "bg-[#ddf2ea] text-[#0b5e4f]",
  FALHOU: "bg-[#fbdad3] text-[#9b2c12]",
};
const NOME_STATUS: Record<MensagemWhatsApp["status"], string> = { SIMULADA: "Simulada", ENVIADA: "Enviada", FALHOU: "Falhou" };

/** Envio de WhatsApp: modo (simulado/oficial), lembretes automáticos, resposta rápida, teste e registro das mensagens. */
export default function WhatsApp() {
  const [s, setS] = useState<SituacaoWhatsApp | null>(null);
  const [mensagens, setMensagens] = useState<MensagemWhatsApp[] | null>(null);
  const [erro, setErro] = useState<string | null>(null);
  const [aviso, setAviso] = useState<string | null>(null);
  const [ocupado, setOcupado] = useState(false);

  const carregarMensagens = useCallback(() => listarMensagens().then(setMensagens).catch((e: Error) => setErro(e.message)), []);

  useEffect(() => {
    buscarWhatsapp().then(setS).catch((e: Error) => setErro(e.message));
    carregarMensagens();
  }, [carregarMensagens]);

  async function acao(fazer: () => Promise<string>) {
    setOcupado(true);
    setErro(null);
    setAviso(null);
    try {
      setAviso(await fazer());
      await carregarMensagens();
    } catch (e) {
      setErro((e as Error).message);
    } finally {
      setOcupado(false);
    }
  }

  if (!s) return erro ? <Erro texto={erro} /> : <Carregando />;

  return (
    <>
      <Titulo sobre="Painel da loja" titulo="WhatsApp">
        <div className="flex flex-wrap gap-2">
          <button type="button" className="btn btn-claro" disabled={ocupado}
            onClick={() => acao(async () => {
              const m = await enviarTesteWhatsapp();
              return m.status === "ENVIADA" ? "Mensagem de teste enviada para o WhatsApp da loja." : "Teste registrado (modo simulado: nada saiu).";
            })}>
            Enviar mensagem de teste
          </button>
          <button type="button" className="btn btn-verde" disabled={ocupado}
            onClick={() => acao(async () => {
              const r = await lembretesAgora();
              return `Lembretes de amanhã: ${r.enviadas} enviados, ${r.simuladas} simulados, ${r.falhas} com falha, ${r.jaFeitas} já tinham saído.`;
            })}>
            Rodar lembretes de amanhã agora
          </button>
        </div>
      </Titulo>

      <section aria-label="Situação" className={`cartao flex flex-col gap-2 ${s.oficial ? "bg-[#ddf2ea]" : "bg-[#fdf3e1]"}`}>
        <h2 className="m-0 text-lg font-extrabold">{s.oficial ? "WhatsApp oficial ligado" : "Modo simulado (nada é enviado)"}</h2>
        <p className="m-0 text-[15px]">
          {s.oficial
            ? "As mensagens saem pela WhatsApp Cloud API oficial da Meta, pelos modelos aprovados."
            : "Sem a WhatsApp Cloud API configurada no servidor, as mensagens ficam registradas aqui embaixo. Use “Abrir no WhatsApp” para mandar por clique. Para envio automático, configure WHATSAPP_TOKEN e WHATSAPP_PHONE_NUMBER_ID (ver README)."}
        </p>
      </section>

      <Erro texto={erro} />
      {aviso && <p role="status" className="m-0 rounded-xl bg-[#ddf2ea] px-4 py-3 font-semibold text-[#0b5e4f]">{aviso}</p>}

      <form
        aria-label="Preferências"
        className="cartao flex flex-col gap-4"
        onSubmit={(e) => {
          e.preventDefault();
          acao(async () => {
            setS(await salvarWhatsapp({ lembretesAutomaticos: s.lembretesAutomaticos, respostaRapida: s.respostaRapida, textoRespostaRapida: s.textoRespostaRapida }));
            return "Preferências salvas.";
          });
        }}
      >
        <h2 className="m-0 text-xl font-extrabold">Envio automático</h2>
        <label className="flex items-start gap-2.5 text-[15px]">
          <input type="checkbox" className="mt-0.5 size-5 accent-verde" checked={s.lembretesAutomaticos} onChange={(e) => setS({ ...s, lembretesAutomaticos: e.target.checked })} />
          <span><b>Lembretes automáticos às 18h</b> · vacina, retorno do banho e agendamentos de amanhã. Cada lembrete sai uma vez só.</span>
        </label>
        <label className="flex items-start gap-2.5 text-[15px]">
          <input type="checkbox" className="mt-0.5 size-5 accent-verde" checked={s.respostaRapida} onChange={(e) => setS({ ...s, respostaRapida: e.target.checked })} />
          <span><b>Resposta rápida</b> · quem mandar mensagem recebe na hora o link do agendamento online (precisa do WhatsApp oficial).</span>
        </label>
        <label className="flex flex-col gap-1.5">
          <span className="rotulo">Texto da resposta rápida ({"{link}"} vira o link do agendamento)</span>
          <textarea className="campo min-h-24 py-2.5" maxLength={500} value={s.textoRespostaRapida}
            placeholder="Oi! Já vamos te responder. Se quiser, agende agora por aqui: {link}"
            onChange={(e) => setS({ ...s, textoRespostaRapida: e.target.value })} />
        </label>
        <button type="submit" className="btn btn-verde self-start" disabled={ocupado}>Salvar preferências</button>
      </form>

      <section aria-label="Mensagens" className="cartao flex flex-col gap-3">
        <h2 className="m-0 text-xl font-extrabold">Últimas mensagens</h2>
        <p className="m-0 text-sm text-muted">Ficam guardadas por 90 dias.</p>
        {!mensagens ? (
          <Carregando />
        ) : mensagens.length === 0 ? (
          <p className="m-0 py-4 text-muted">Nenhuma mensagem ainda. Use os botões acima para testar.</p>
        ) : (
          <ul className="m-0 flex list-none flex-col p-0">
            {mensagens.map((m) => (
              <li key={m.id} className="flex flex-wrap items-start gap-x-4 gap-y-2 border-b border-linha py-3 last:border-0">
                <span className="flex min-w-0 flex-[1_1_320px] flex-col gap-1">
                  <span className="text-sm font-bold">
                    {NOMES_MENSAGEM[m.tipo]} · para {m.para} ·{" "}
                    <span className="font-medium text-muted">{new Date(m.criadoEm).toLocaleString("pt-BR", { timeZone: "America/Sao_Paulo", dateStyle: "short", timeStyle: "short" })}</span>
                  </span>
                  <span className="whitespace-pre-line text-[15px]">{m.texto}</span>
                  {m.erro && <span className="text-sm text-[#9b2c12]">{m.erro}</span>}
                </span>
                <span className={`chip ${COR_STATUS[m.status]}`}>{NOME_STATUS[m.status]}</span>
                {m.status !== "ENVIADA" && (
                  <a href={linkWhatsapp(m.para, m.texto)} target="_blank" rel="noopener noreferrer" className="btn btn-claro min-h-10">Abrir no WhatsApp</a>
                )}
              </li>
            ))}
          </ul>
        )}
      </section>
    </>
  );
}
