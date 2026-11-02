"use client";

import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { Fragment, useCallback, useEffect, useState } from "react";
import { FormCliente } from "@/components/FormCliente";
import { BotaoExcluir, Carregando, Erro } from "@/components/Ui";
import { alterarTutor, alterarVacina, buscarTutor, criarPet, excluirTutor, excluirVacina, listarVacinas, registrarVacina, type NovaVacina, type NovoPet } from "@/lib/api";
import { formatar, hoje, proximoBanho, situacaoVacina } from "@/lib/datas";
import { linkWhatsapp } from "@/lib/loja";
import type { AplicacaoVacina, Pet, Tutor } from "@/lib/tipos";

export default function FichaCliente() {
  const { id } = useParams<{ id: string }>();
  const [tutor, setTutor] = useState<Tutor | null>(null);
  const [petId, setPetId] = useState<number | null>(null);
  const [erro, setErro] = useState<string | null>(null);
  const [novoPet, setNovoPet] = useState(false);
  const [editando, setEditando] = useState(false);
  const router = useRouter();

  const carregar = useCallback(async () => {
    try {
      const t = await buscarTutor(Number(id));
      setTutor(t);
      setPetId((atual) => atual ?? t.pets[0]?.id ?? null);
    } catch (e) {
      setErro((e as Error).message);
    }
  }, [id]);

  useEffect(() => {
    carregar();
  }, [carregar]);

  if (erro && !tutor) return <Erro texto={erro} />;
  if (!tutor) return <Carregando />;

  const pet = tutor.pets.find((p) => p.id === petId) ?? null;
  const iniciais = tutor.nome.split(" ").map((p) => p[0]).slice(0, 2).join("");

  return (
    <>
      <Link href="/painel/clientes" className="self-start text-sm font-bold text-verde">← Clientes</Link>

      <section aria-label="Cliente" className="cartao flex flex-wrap items-center justify-between gap-5 p-6">
        <div className="flex items-center gap-4">
          <span className="inline-flex size-15 items-center justify-center rounded-full bg-[#ddf2ea] text-[22px] font-extrabold text-[#0b5e4f]">{iniciais}</span>
          <div className="flex flex-col">
            <h1 className="m-0 text-[26px] font-extrabold">{tutor.nome}</h1>
            <span className="text-sm text-muted">{tutor.pets.length} {tutor.pets.length === 1 ? "pet" : "pets"}</span>
          </div>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <a href={linkWhatsapp(tutor.whatsapp, `Olá, ${tutor.nome.split(" ")[0]}!`)} target="_blank" rel="noopener noreferrer" className="btn btn-claro">
            Chamar no WhatsApp
          </a>
          <button type="button" className="btn btn-claro" aria-expanded={editando} onClick={() => setEditando((v) => !v)}>Editar</button>
          <BotaoExcluir
            pergunta={`Excluir ${tutor.nome}? Cadastro por engano é apagado; se já pagou atendimentos, os valores ficam no financeiro sem o nome.`}
            aoConfirmar={async () => {
              try {
                await excluirTutor(tutor.id);
                router.replace("/painel/clientes");
              } catch (e) {
                setErro((e as Error).message);
              }
            }}
          />
        </div>
        <dl className="m-0 grid flex-[1_1_100%] grid-cols-[repeat(auto-fit,minmax(180px,1fr))] gap-3 border-t border-linha pt-4">
          {[
            ["WhatsApp", tutor.whatsapp],
            ["Endereço", tutor.endereco || "—"],
            ["CPF (opcional)", tutor.cpf || "Não informado"],
            ["Chegou por", tutor.origem || "—"],
          ].map(([r, v]) => (
            <div key={r}>
              <dt className="text-xs font-bold uppercase tracking-wider text-muted">{r}</dt>
              <dd className="m-0 mt-1 font-semibold">{v}</dd>
            </div>
          ))}
        </dl>
      </section>

      {editando && (
        <FormCliente
          inicial={{ nome: tutor.nome, whatsapp: tutor.whatsapp, endereco: tutor.endereco, cpf: tutor.cpf, origem: tutor.origem || "WhatsApp" }}
          textoBotao="Salvar alterações"
          aoSalvar={async (dados) => {
            setTutor(await alterarTutor(tutor.id, dados));
            setEditando(false);
          }}
          aoCancelar={() => setEditando(false)}
        />
      )}
      <Erro texto={erro} />

      <div role="tablist" aria-label="Pets do cliente" className="flex flex-wrap gap-2.5">
        {tutor.pets.map((p) => (
          <button
            key={p.id}
            type="button"
            role="tab"
            aria-selected={p.id === petId}
            onClick={() => setPetId(p.id)}
            className={`flex min-h-14 flex-col items-start justify-center rounded-2xl border-2 px-5 leading-tight ${p.id === petId ? "border-verde-escuro bg-verde-escuro text-menta" : "border-borda bg-white text-ink"}`}
          >
            <span className="font-extrabold">{p.nome}</span>
            <span className="text-[13px]">{p.especie} · {p.raca || "sem raça definida"}</span>
          </button>
        ))}
        <button type="button" className="btn btn-claro min-h-14" onClick={() => setNovoPet((v) => !v)}>+ Novo pet</button>
      </div>

      {novoPet && (
        <NovoPetForm
          tutorId={tutor.id}
          aoSalvar={(p) => {
            setNovoPet(false);
            setPetId(p.id);
            carregar();
          }}
        />
      )}

      {pet ? <PainelPet pet={pet} /> : <p className="m-0 text-muted">Cadastre o primeiro pet deste cliente.</p>}
    </>
  );
}

function PainelPet({ pet }: { pet: Pet }) {
  const [vacinas, setVacinas] = useState<AplicacaoVacina[] | null>(null);
  const [nova, setNova] = useState(false);
  const [editandoId, setEditandoId] = useState<number | null>(null);
  const [erroVacina, setErroVacina] = useState<string | null>(null);

  useEffect(() => {
    setVacinas(null);
    listarVacinas(pet.id).then(setVacinas);
  }, [pet.id]);

  const proximo = proximoBanho(pet.ultimoBanho, pet.intervaloBanhoDias);

  return (
    <div className="flex flex-wrap items-start gap-5">
      <section aria-label="Carteira de vacinas" className="cartao flex min-w-0 flex-[999_1_520px] flex-col gap-3.5 p-6">
        <div className="flex flex-wrap items-center justify-between gap-2.5">
          <h2 className="m-0 text-xl font-extrabold">Carteira de vacinas · {pet.nome}</h2>
          <button type="button" className="btn btn-claro min-h-10 text-sm" onClick={() => setNova((v) => !v)}>+ Registrar vacina</button>
        </div>
        {nova && (
          <NovaVacinaForm
            petId={pet.id}
            aoSalvar={(v) => {
              setNova(false);
              setVacinas((l) => [v, ...(l ?? [])]);
            }}
          />
        )}
        <Erro texto={erroVacina} />
        {!vacinas ? (
          <Carregando />
        ) : vacinas.length === 0 ? (
          <p className="m-0 text-muted">Nenhuma vacina registrada.</p>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full min-w-[520px] border-collapse text-[15px]">
              <thead>
                <tr className="text-left text-xs uppercase tracking-wider text-muted">
                  {["Vacina", "Dose", "Aplicada", "Próxima", "Situação"].map((c) => (
                    <th key={c} scope="col" className="border-b border-linha px-2 py-2.5">{c}</th>
                  ))}
                  <th scope="col" className="border-b border-linha px-2 py-2.5"><span className="sr-only">Ações</span></th>
                </tr>
              </thead>
              <tbody>
                {vacinas.map((v) => {
                  const s = situacaoVacina(v.proximaEm);
                  return (
                    <Fragment key={v.id}>
                      <tr>
                        <td className="border-b border-linha px-2 py-3.5 font-bold">{v.vacina}</td>
                        <td className="border-b border-linha px-2 py-3.5">{v.dose}</td>
                        <td className="border-b border-linha px-2 py-3.5">{formatar(v.aplicadaEm, true)}</td>
                        <td className="border-b border-linha px-2 py-3.5 font-bold">{formatar(v.proximaEm, true)}</td>
                        <td className="border-b border-linha px-2 py-3.5"><span className={`chip ${s.classe}`}>{s.texto}</span></td>
                        <td className="border-b border-linha px-2 py-2">
                          <span className="flex flex-wrap justify-end gap-2">
                            <button type="button" className="btn btn-claro min-h-10" aria-expanded={editandoId === v.id} onClick={() => setEditandoId(editandoId === v.id ? null : v.id)}>
                              Editar
                            </button>
                            <BotaoExcluir
                              pergunta={`Excluir ${v.vacina}${v.dose ? ` (${v.dose})` : ""}?`}
                              aoConfirmar={async () => {
                                try {
                                  await excluirVacina(pet.id, v.id);
                                  setVacinas((l) => l?.filter((x) => x.id !== v.id) ?? null);
                                  setErroVacina(null);
                                } catch (e) {
                                  setErroVacina((e as Error).message);
                                }
                              }}
                            />
                          </span>
                        </td>
                      </tr>
                      {editandoId === v.id && (
                        <tr>
                          <td colSpan={6} className="border-b border-linha py-3">
                            <NovaVacinaForm
                              petId={pet.id}
                              editando={v}
                              aoSalvar={(nova) => {
                                setEditandoId(null);
                                setVacinas((l) => l?.map((x) => (x.id === nova.id ? nova : x)) ?? null);
                              }}
                              aoCancelar={() => setEditandoId(null)}
                            />
                          </td>
                        </tr>
                      )}
                    </Fragment>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </section>

      <aside className="flex min-w-0 flex-[1_1_300px] flex-col gap-5">
        <section aria-label="Banho e tosa" className="flex flex-col gap-3 rounded-[22px] bg-verde-escuro p-6 text-menta">
          <h2 className="m-0 text-lg font-extrabold">Banho e tosa</h2>
          {[
            ["Retorno", pet.intervaloBanhoDias ? `A cada ${pet.intervaloBanhoDias} dias` : "Sem retorno automático"],
            ["Último", formatar(pet.ultimoBanho)],
          ].map(([r, v]) => (
            <div key={r} className="flex justify-between gap-2.5">
              <span className="text-menta-2">{r}</span>
              <span className="font-bold">{v}</span>
            </div>
          ))}
          <div className="flex justify-between gap-2.5">
            <span className="text-menta-2">Próximo</span>
            <span className="font-extrabold text-caramelo">{formatar(proximo)}</span>
          </div>
          <p className="m-0 text-[13px] text-menta-2">O cliente recebe o convite no WhatsApp na véspera.</p>
        </section>
        <section aria-label="Sobre o pet" className="cartao flex flex-col gap-2.5 p-6">
          <h2 className="m-0 text-lg font-extrabold">Sobre o {pet.nome}</h2>
          <div className="flex justify-between gap-2.5"><span className="text-muted">Espécie e raça</span><span className="text-right font-bold">{pet.especie} · {pet.raca || "—"}</span></div>
          <div className="flex justify-between gap-2.5"><span className="text-muted">Peso</span><span className="font-bold">{pet.pesoKg ? `${pet.pesoKg} kg` : "—"}</span></div>
          {pet.observacoes && <p className="m-0 mt-1.5 rounded-xl bg-[#fdf3e1] p-3 text-sm text-[#6b4a12]">{pet.observacoes}</p>}
        </section>
      </aside>
    </div>
  );
}

function NovoPetForm({ tutorId, aoSalvar }: { tutorId: number; aoSalvar: (p: Pet) => void }) {
  const [f, setF] = useState<NovoPet>({ nome: "", especie: "Cão", raca: "", pesoKg: null, observacoes: "", intervaloBanhoDias: 15 });
  const [erro, setErro] = useState<string | null>(null);

  async function salvar(e: React.FormEvent) {
    e.preventDefault();
    if (!f.nome.trim()) return setErro("Informe o nome do pet.");
    try {
      aoSalvar(await criarPet(tutorId, f));
    } catch (er) {
      setErro((er as Error).message);
    }
  }

  return (
    <form onSubmit={salvar} className="cartao grid grid-cols-[repeat(auto-fit,minmax(160px,1fr))] items-end gap-3.5">
      <label className="flex flex-col gap-1.5"><span className="rotulo">Nome</span>
        <input className="campo" value={f.nome} onChange={(e) => setF({ ...f, nome: e.target.value })} /></label>
      <label className="flex flex-col gap-1.5"><span className="rotulo">Espécie</span>
        <select className="campo" value={f.especie} onChange={(e) => setF({ ...f, especie: e.target.value as Pet["especie"] })}>
          <option>Cão</option><option>Gato</option><option>Outro</option>
        </select></label>
      <label className="flex flex-col gap-1.5"><span className="rotulo">Raça</span>
        <input className="campo" value={f.raca} onChange={(e) => setF({ ...f, raca: e.target.value })} /></label>
      <label className="flex flex-col gap-1.5"><span className="rotulo">Peso (kg)</span>
        <input className="campo" type="number" min="0" step="0.1" value={f.pesoKg ?? ""} onChange={(e) => setF({ ...f, pesoKg: e.target.value ? Number(e.target.value) : null })} /></label>
      <label className="flex flex-col gap-1.5"><span className="rotulo">Banho a cada (dias)</span>
        <input className="campo" type="number" min="0" value={f.intervaloBanhoDias ?? ""} onChange={(e) => setF({ ...f, intervaloBanhoDias: e.target.value ? Number(e.target.value) : null })} /></label>
      <label className="col-span-full flex flex-col gap-1.5"><span className="rotulo">Observações de manejo</span>
        <input className="campo" value={f.observacoes} onChange={(e) => setF({ ...f, observacoes: e.target.value })} /></label>
      <button type="submit" className="btn btn-verde">Salvar pet</button>
      <div className="col-span-full"><Erro texto={erro} /></div>
    </form>
  );
}

/** Registrar vacina, ou corrigir uma existente quando recebe "editando". */
function NovaVacinaForm({ petId, editando, aoSalvar, aoCancelar }: {
  petId: number;
  editando?: AplicacaoVacina;
  aoSalvar: (v: AplicacaoVacina) => void;
  aoCancelar?: () => void;
}) {
  const [f, setF] = useState<NovaVacina>(
    editando
      ? { vacina: editando.vacina, dose: editando.dose, aplicadaEm: editando.aplicadaEm, proximaEm: editando.proximaEm }
      : { vacina: "", dose: "", aplicadaEm: hoje(), proximaEm: null },
  );
  const [erro, setErro] = useState<string | null>(null);

  async function salvar(e: React.FormEvent) {
    e.preventDefault();
    if (!f.vacina.trim()) return setErro("Informe a vacina.");
    try {
      aoSalvar(editando ? await alterarVacina(petId, editando.id, f) : await registrarVacina(petId, f));
    } catch (er) {
      setErro((er as Error).message);
    }
  }

  return (
    <form onSubmit={salvar} className="grid grid-cols-[repeat(auto-fit,minmax(150px,1fr))] items-end gap-3 rounded-2xl bg-fundo p-4">
      <label className="flex flex-col gap-1.5"><span className="rotulo">Vacina</span>
        <input className="campo" placeholder="Ex.: V10" value={f.vacina} onChange={(e) => setF({ ...f, vacina: e.target.value })} /></label>
      <label className="flex flex-col gap-1.5"><span className="rotulo">Dose</span>
        <input className="campo" list="doses" placeholder="Ex.: 2ª dose" value={f.dose} onChange={(e) => setF({ ...f, dose: e.target.value })} /></label>
      <datalist id="doses">
        {["1ª dose", "2ª dose", "3ª dose", "Reforço anual", "Dose única"].map((d) => <option key={d} value={d} />)}
      </datalist>
      <label className="flex flex-col gap-1.5"><span className="rotulo">Aplicada em</span>
        <input className="campo" type="date" value={f.aplicadaEm} onChange={(e) => setF({ ...f, aplicadaEm: e.target.value })} /></label>
      <label className="flex flex-col gap-1.5"><span className="rotulo">Próxima dose</span>
        <input className="campo" type="date" value={f.proximaEm ?? ""} onChange={(e) => setF({ ...f, proximaEm: e.target.value || null })} /></label>
      <div className="flex gap-2">
        <button type="submit" className="btn btn-verde flex-1">{editando ? "Salvar alterações" : "Registrar"}</button>
        {aoCancelar && <button type="button" className="btn btn-claro" onClick={aoCancelar}>Cancelar</button>}
      </div>
      <div className="col-span-full"><Erro texto={erro} /></div>
    </form>
  );
}
