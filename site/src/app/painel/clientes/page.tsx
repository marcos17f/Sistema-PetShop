"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { Carregando, Erro, Titulo } from "@/components/Ui";
import { criarTutor, listarTutores, type NovoTutor } from "@/lib/api";
import type { Tutor } from "@/lib/tipos";

const VAZIO: NovoTutor = { nome: "", whatsapp: "", endereco: "", cpf: "", origem: "WhatsApp" };

export default function Clientes() {
  const [busca, setBusca] = useState("");
  const [lista, setLista] = useState<Tutor[] | null>(null);
  const [erro, setErro] = useState<string | null>(null);
  const [novo, setNovo] = useState(false);
  const [form, setForm] = useState<NovoTutor>(VAZIO);

  useEffect(() => {
    const t = setTimeout(() => {
      listarTutores(busca).then(setLista).catch((e: Error) => setErro(e.message));
    }, 250);
    return () => clearTimeout(t);
  }, [busca]);

  async function salvar(e: React.FormEvent) {
    e.preventDefault();
    if (form.nome.trim().length < 3 || form.whatsapp.replace(/\D/g, "").length < 10) {
      return setErro("Informe o nome e o WhatsApp com DDD.");
    }
    try {
      const t = await criarTutor(form);
      setLista((l) => [t, ...(l ?? [])]);
      setForm(VAZIO);
      setNovo(false);
      setErro(null);
    } catch (er) {
      setErro((er as Error).message);
    }
  }

  return (
    <>
      <Titulo titulo="Clientes e pets">
        <button type="button" className="btn btn-verde" onClick={() => setNovo((v) => !v)}>+ Novo cliente</button>
      </Titulo>

      {novo && (
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
          <button type="submit" className="btn btn-verde">Salvar cliente</button>
        </form>
      )}

      <label className="flex flex-col gap-1.5">
        <span className="rotulo">Buscar por nome, WhatsApp ou nome do pet</span>
        <input className="campo max-w-[460px]" type="search" value={busca} onChange={(e) => setBusca(e.target.value)} />
      </label>

      <Erro texto={erro} />

      <section className="cartao">
        {!lista ? (
          <Carregando />
        ) : lista.length === 0 ? (
          <p className="m-0 py-4 text-muted">Nenhum cliente encontrado.</p>
        ) : (
          <ul className="m-0 flex list-none flex-col p-0">
            {lista.map((t) => (
              <li key={t.id} className="border-b border-linha last:border-0">
                <Link href={`/painel/clientes/${t.id}`} className="flex flex-wrap items-center gap-x-5 gap-y-1 px-1 py-4 text-ink no-underline hover:bg-fundo">
                  <span className="flex-[1_1_200px] font-bold">{t.nome}</span>
                  <span className="flex-[1_1_160px] text-muted">{t.whatsapp}</span>
                  <span className="flex-[1_1_200px] text-sm text-muted">
                    {t.pets.length ? t.pets.map((p) => p.nome).join(", ") : "Sem pets cadastrados"}
                  </span>
                  <span aria-hidden="true" className="text-verde">→</span>
                </Link>
              </li>
            ))}
          </ul>
        )}
      </section>
    </>
  );
}
