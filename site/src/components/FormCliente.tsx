"use client";

import { useState } from "react";
import { Erro } from "@/components/Ui";
import type { NovoTutor } from "@/lib/api";

export const CLIENTE_VAZIO: NovoTutor = { nome: "", whatsapp: "", endereco: "", cpf: "", origem: "WhatsApp" };

/** Formulário do cliente, usado para cadastrar (lista) e para editar (ficha). */
export function FormCliente({ inicial, textoBotao, aoSalvar, aoCancelar }: {
  inicial: NovoTutor;
  textoBotao: string;
  aoSalvar: (dados: NovoTutor) => Promise<void>;
  aoCancelar?: () => void;
}) {
  const [form, setForm] = useState<NovoTutor>(inicial);
  const [erro, setErro] = useState<string | null>(null);
  const [salvando, setSalvando] = useState(false);

  async function salvar(e: React.FormEvent) {
    e.preventDefault();
    if (form.nome.trim().length < 3 || form.whatsapp.replace(/\D/g, "").length < 10) {
      return setErro("Informe o nome e o WhatsApp com DDD.");
    }
    setSalvando(true);
    try {
      await aoSalvar(form);
      setErro(null);
    } catch (er) {
      setErro((er as Error).message);
    } finally {
      setSalvando(false);
    }
  }

  return (
    <form onSubmit={salvar} className="cartao grid grid-cols-[repeat(auto-fit,minmax(200px,1fr))] items-end gap-3.5">
      <label className="flex flex-col gap-1.5">
        <span className="rotulo">Nome</span>
        <input className="campo" value={form.nome} onChange={(e) => setForm({ ...form, nome: e.target.value })} />
      </label>
      <label className="flex flex-col gap-1.5">
        <span className="rotulo">WhatsApp com DDD</span>
        <input className="campo" type="tel" value={form.whatsapp} onChange={(e) => setForm({ ...form, whatsapp: e.target.value })} />
      </label>
      <label className="flex flex-col gap-1.5">
        <span className="rotulo">Endereço</span>
        <input className="campo" value={form.endereco} onChange={(e) => setForm({ ...form, endereco: e.target.value })} />
      </label>
      <label className="flex flex-col gap-1.5">
        <span className="rotulo">CPF (opcional)</span>
        <input className="campo" inputMode="numeric" value={form.cpf} onChange={(e) => setForm({ ...form, cpf: e.target.value })} />
      </label>
      <label className="flex flex-col gap-1.5">
        <span className="rotulo">Chegou por</span>
        <select className="campo" value={form.origem} onChange={(e) => setForm({ ...form, origem: e.target.value })}>
          {["WhatsApp", "Instagram", "Google", "Indicação", "Passou na loja"].map((o) => <option key={o}>{o}</option>)}
        </select>
      </label>
      <div className="flex gap-2">
        <button type="submit" disabled={salvando} className="btn btn-verde flex-1">{salvando ? "Salvando…" : textoBotao}</button>
        {aoCancelar && <button type="button" className="btn btn-claro" onClick={aoCancelar}>Cancelar</button>}
      </div>
      <div className="col-span-full empty:hidden"><Erro texto={erro} /></div>
    </form>
  );
}
