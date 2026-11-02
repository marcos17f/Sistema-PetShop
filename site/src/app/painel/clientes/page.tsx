"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { CLIENTE_VAZIO, FormCliente } from "@/components/FormCliente";
import { Carregando, Erro, Titulo } from "@/components/Ui";
import { criarTutor, listarTutores } from "@/lib/api";
import type { Tutor } from "@/lib/tipos";

export default function Clientes() {
  const [busca, setBusca] = useState("");
  const [lista, setLista] = useState<Tutor[] | null>(null);
  const [erro, setErro] = useState<string | null>(null);
  const [novo, setNovo] = useState(false);

  useEffect(() => {
    const t = setTimeout(() => {
      listarTutores(busca).then(setLista).catch((e: Error) => setErro(e.message));
    }, 250);
    return () => clearTimeout(t);
  }, [busca]);

  return (
    <>
      <Titulo titulo="Clientes e pets">
        <button type="button" className="btn btn-verde" onClick={() => setNovo((v) => !v)}>+ Novo cliente</button>
      </Titulo>

      {novo && (
        <FormCliente
          inicial={CLIENTE_VAZIO}
          textoBotao="Salvar cliente"
          aoSalvar={async (dados) => {
            const t = await criarTutor(dados);
            setLista((l) => [t, ...(l ?? [])]);
            setNovo(false);
          }}
        />
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
