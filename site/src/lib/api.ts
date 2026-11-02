/**
 * Cliente da API do pet shop (Spring Boot). É como um service com HttpClient no Angular:
 * as telas só chamam estas funções e não sabem se os dados vêm da API ou da demonstração.
 */
import { loja } from "./loja";
import {
  calcularLembretes, calcularResumoMes, comNomes, db, descrever, garantirRecorrentes, novoId, registrar, tutorCompleto, type LancamentoDemo,
} from "./demo";
import { hoje, somarMeses } from "./datas";
import { podeRepetir } from "./tipos";
import type {
  AplicacaoVacina, Agendamento, Categoria, Lancamento, Lembrete, Pet, PrecoServico, RegistroHistorico, Resumo, StatusAgendamento, TipoLancamento,
  TipoServico, Tutor,
} from "./tipos";

const API_URL = process.env.NEXT_PUBLIC_API_URL;
export const modoDemonstracao = !API_URL;
const CHAVE_SESSAO = "petshop-auth";

/* ---------- autenticação (HTTP Basic, guardada só nesta aba) ---------- */

function credencial(): string | null {
  try {
    return sessionStorage.getItem(CHAVE_SESSAO);
  } catch {
    return null;
  }
}

export function estaLogado() {
  return modoDemonstracao || !!credencial();
}

export async function entrar(usuario: string, senha: string) {
  if (modoDemonstracao) return;
  const token = btoa(`${usuario}:${senha}`);
  const res = await fetch(`${API_URL}/api/lojas/${loja.slug}/tutores`, { headers: { Authorization: `Basic ${token}` } });
  if (res.status === 401 || res.status === 403) throw new Error("Usuário ou senha inválidos.");
  if (!res.ok) throw new Error("Não foi possível conectar ao sistema.");
  sessionStorage.setItem(CHAVE_SESSAO, token);
}

export function sair() {
  try {
    sessionStorage.removeItem(CHAVE_SESSAO);
  } catch {
    /* sem storage: nada a limpar */
  }
}

async function chamar<T>(caminho: string, init: RequestInit = {}): Promise<T> {
  const res = await fetch(`${API_URL}/api/lojas/${loja.slug}${caminho}`, {
    ...init,
    headers: { "Content-Type": "application/json", Authorization: `Basic ${credencial() ?? ""}`, ...init.headers },
    cache: "no-store",
  });
  if (res.status === 401) {
    sair();
    throw new Error("Sessão expirada. Entre novamente.");
  }
  if (!res.ok) {
    const corpo = await res.json().catch(() => null);
    const campos = corpo?.campos ? Object.values(corpo.campos as Record<string, string>) : [];
    throw new Error(campos.length ? campos.join(" · ") : (corpo?.detail ?? "Não foi possível concluir. Tente novamente."));
  }
  return res.status === 204 ? (undefined as T) : res.json();
}

const espera = () => new Promise((r) => setTimeout(r, 150));

/* ---------- clientes e pets ---------- */

export type NovoTutor = Omit<Tutor, "id" | "pets">;
export type NovoPet = Omit<Pet, "id" | "tutorId">;

export async function listarTutores(busca = ""): Promise<Tutor[]> {
  if (!modoDemonstracao) return chamar(`/tutores?busca=${encodeURIComponent(busca)}`);
  await espera();
  const b = busca.toLowerCase();
  return db.tutores
    .filter((t) => !t.removido)
    .map(tutorCompleto)
    .filter((t) => !b || t.nome.toLowerCase().includes(b) || t.whatsapp.includes(b) || t.pets.some((p) => p.nome.toLowerCase().includes(b)));
}

export async function buscarTutor(id: number): Promise<Tutor> {
  if (!modoDemonstracao) return chamar(`/tutores/${id}`);
  await espera();
  const t = db.tutores.find((x) => x.id === id && !x.removido);
  if (!t) throw new Error("Cliente não encontrado.");
  return tutorCompleto(t);
}

export async function alterarTutor(id: number, dados: NovoTutor): Promise<Tutor> {
  if (!modoDemonstracao) return chamar(`/tutores/${id}`, { method: "PUT", body: JSON.stringify(dados) });
  const t = db.tutores.find((x) => x.id === id && !x.removido);
  if (!t) throw new Error("Cliente não encontrado.");
  Object.assign(t, { ...dados, nome: dados.nome.trim(), whatsapp: dados.whatsapp.replace(/\D/g, "") });
  return tutorCompleto(t);
}

/**
 * Cadastro por engano (sem atendimento concluído): apaga tudo do cliente. Com atendimento concluído: apaga os dados
 * pessoais (LGPD), cancela os agendamentos futuros e mantém os valores no financeiro como "Cliente removido".
 */
export async function excluirTutor(id: number): Promise<void> {
  if (!modoDemonstracao) return chamar(`/tutores/${id}`, { method: "DELETE" });
  const t = db.tutores.find((x) => x.id === id)!;
  const petIds = db.pets.filter((p) => p.tutorId === id).map((p) => p.id);
  const doCliente = (a: { petId: number }) => petIds.includes(a.petId);
  const qtd = `cliente #${id} · ${petIds.length} pet(s)`;
  if (db.agendamentos.some((a) => doCliente(a) && a.status === "CONCLUIDO")) {
    db.agendamentos.filter((a) => doCliente(a) && a.data >= hoje() && a.status !== "CONCLUIDO").forEach((a) => (a.status = "CANCELADO"));
    Object.assign(t, { nome: "Cliente removido", whatsapp: "", endereco: "", cpf: "", origem: "", removido: true });
    registrar("EXCLUIU", "cliente", id, qtd, "dados pessoais apagados; atendimentos concluídos mantidos no financeiro");
  } else {
    db.agendamentos = db.agendamentos.filter((a) => !doCliente(a));
    db.vacinas = db.vacinas.filter((v) => !petIds.includes(v.petId));
    db.pets = db.pets.filter((p) => p.tutorId !== id);
    db.tutores = db.tutores.filter((x) => x.id !== id);
    registrar("EXCLUIU", "cliente", id, qtd, "apagado por completo (sem atendimento concluído)");
  }
}

export async function criarTutor(dados: NovoTutor): Promise<Tutor> {
  if (!modoDemonstracao) return chamar(`/tutores`, { method: "POST", body: JSON.stringify(dados) });
  const t = { ...dados, id: novoId() };
  db.tutores.push(t);
  return tutorCompleto(t);
}

export async function criarPet(tutorId: number, dados: NovoPet): Promise<Pet> {
  if (!modoDemonstracao) return chamar(`/tutores/${tutorId}/pets`, { method: "POST", body: JSON.stringify(dados) });
  validarPet(dados);
  const p: Pet = { ...dados, id: novoId(), tutorId };
  db.pets.push(p);
  return p;
}

export async function alterarPet(tutorId: number, petId: number, dados: NovoPet): Promise<Pet> {
  if (!modoDemonstracao) return chamar(`/tutores/${tutorId}/pets/${petId}`, { method: "PUT", body: JSON.stringify(dados) });
  validarPet(dados);
  const p = db.pets.find((x) => x.id === petId && x.tutorId === tutorId);
  if (!p) throw new Error("Pet não encontrado");
  Object.assign(p, { ...dados, nome: dados.nome.trim() });
  return p;
}

/** Pet cadastrado por engano: apaga com vacinas e agendamentos. Com atendimento concluído, não (está no financeiro). */
export async function excluirPet(tutorId: number, petId: number): Promise<void> {
  if (!modoDemonstracao) return chamar(`/tutores/${tutorId}/pets/${petId}`, { method: "DELETE" });
  if (db.agendamentos.some((a) => a.petId === petId && a.status === "CONCLUIDO")) {
    throw new Error("Esse pet tem atendimentos concluídos no financeiro e não pode ser excluído.");
  }
  db.agendamentos = db.agendamentos.filter((a) => a.petId !== petId);
  db.vacinas = db.vacinas.filter((v) => v.petId !== petId);
  db.pets = db.pets.filter((x) => x.id !== petId);
}

/** Mesmas regras da API (demonstração). */
function validarPet(d: NovoPet) {
  if (d.intervaloBanhoDias !== null && (d.intervaloBanhoDias < 1 || d.intervaloBanhoDias > 365)) {
    throw new Error("O banho deve ser a cada 1 a 365 dias");
  }
  if (d.ultimoBanho && d.ultimoBanho > hoje()) throw new Error("O último banho não pode ser no futuro");
}

/* ---------- vacinas ---------- */

export type NovaVacina = Omit<AplicacaoVacina, "id" | "petId">;

export async function listarVacinas(petId: number): Promise<AplicacaoVacina[]> {
  if (!modoDemonstracao) return chamar(`/pets/${petId}/vacinas`);
  await espera();
  return db.vacinas.filter((v) => v.petId === petId).sort((a, b) => b.aplicadaEm.localeCompare(a.aplicadaEm));
}

export async function registrarVacina(petId: number, dados: NovaVacina): Promise<AplicacaoVacina> {
  if (!modoDemonstracao) return chamar(`/pets/${petId}/vacinas`, { method: "POST", body: JSON.stringify(dados) });
  validarVacina(dados);
  const v = { ...dados, id: novoId(), petId };
  db.vacinas.push(v);
  return v;
}

export async function alterarVacina(petId: number, id: number, dados: NovaVacina): Promise<AplicacaoVacina> {
  if (!modoDemonstracao) return chamar(`/pets/${petId}/vacinas/${id}`, { method: "PUT", body: JSON.stringify(dados) });
  validarVacina(dados);
  const v = db.vacinas.find((x) => x.id === id && x.petId === petId);
  if (!v) throw new Error("Vacina não encontrada");
  Object.assign(v, dados);
  return v;
}

/** Apaga uma vacina registrada por engano. */
export async function excluirVacina(petId: number, id: number): Promise<void> {
  if (!modoDemonstracao) return chamar(`/pets/${petId}/vacinas/${id}`, { method: "DELETE" });
  db.vacinas = db.vacinas.filter((x) => !(x.id === id && x.petId === petId));
}

/** Mesmas regras da API (demonstração). */
function validarVacina(d: NovaVacina) {
  if (d.aplicadaEm > hoje()) throw new Error("A data não pode ser futura");
  if (d.proximaEm && d.proximaEm <= d.aplicadaEm) throw new Error("O reforço deve ser depois da aplicação");
}

/* ---------- agenda ---------- */

export type NovoAgendamento = Pick<Agendamento, "petId" | "data" | "hora" | "tipo" | "descricao">;

export async function listarAgenda(data: string): Promise<Agendamento[]> {
  if (!modoDemonstracao) return chamar(`/agenda?data=${data}`);
  await espera();
  return db.agendamentos.filter((a) => a.data === data).sort((a, b) => a.hora.localeCompare(b.hora)).map(comNomes);
}

export async function criarAgendamento(dados: NovoAgendamento): Promise<Agendamento> {
  if (!modoDemonstracao) return chamar(`/agenda`, { method: "POST", body: JSON.stringify(dados) });
  garantirIntervalo(dados.data, dados.hora, null);
  const a = { ...dados, id: novoId(), status: "AGUARDANDO" as StatusAgendamento, valor: null };
  db.agendamentos.push(a);
  return comNomes(a);
}

/** Corrige um agendamento lançado errado (concluído e cancelado não são editados, como na API). */
export async function alterarAgendamento(id: number, dados: NovoAgendamento): Promise<Agendamento> {
  if (!modoDemonstracao) return chamar(`/agenda/${id}`, { method: "PUT", body: JSON.stringify(dados) });
  const a = db.agendamentos.find((x) => x.id === id)!;
  if (a.status === "CONCLUIDO" || a.status === "CANCELADO") {
    throw new Error("Atendimento concluído ou cancelado não pode ser editado. Mude a situação antes, se foi engano.");
  }
  garantirIntervalo(dados.data, dados.hora, id);
  Object.assign(a, dados);
  return comNomes(a);
}

/** Apaga um agendamento lançado por engano. Concluído não: o valor já entrou no financeiro. */
export async function excluirAgendamento(id: number): Promise<void> {
  if (!modoDemonstracao) return chamar(`/agenda/${id}`, { method: "DELETE" });
  if (db.agendamentos.find((x) => x.id === id)?.status === "CONCLUIDO") {
    throw new Error("Atendimento concluído já entrou no financeiro e não pode ser excluído. Se foi engano, mude a situação antes.");
  }
  db.agendamentos = db.agendamentos.filter((x) => x.id !== id);
}

/** Mesma regra da API: pelo menos loja.intervaloAgendaMinutos entre um atendimento e outro, de qualquer cliente. */
function garantirIntervalo(data: string, hora: string, ignorar: number | null) {
  const minutos = (h: string) => Number(h.slice(0, 2)) * 60 + Number(h.slice(3, 5));
  const perto = db.agendamentos.find(
    (x) => x.id !== ignorar && x.data === data && x.status !== "CANCELADO" && Math.abs(minutos(x.hora) - minutos(hora)) < loja.intervaloAgendaMinutos,
  );
  if (perto) {
    throw new Error(`Muito perto do atendimento das ${perto.hora}. Deixe pelo menos ${loja.intervaloAgendaMinutos} minutos entre um atendimento e outro.`);
  }
}

/** valor é opcional: ao concluir sem valor, a API usa o preço padrão do serviço. */
export async function mudarStatus(id: number, status: StatusAgendamento, valor?: number): Promise<Agendamento> {
  if (!modoDemonstracao) return chamar(`/agenda/${id}/status`, { method: "PATCH", body: JSON.stringify({ status, valor }) });
  const a = db.agendamentos.find((x) => x.id === id)!;
  if (a.status === "CANCELADO" && status !== "CANCELADO") garantirIntervalo(a.data, a.hora, id);
  const antes = { status: a.status, valor: a.valor };
  a.status = status;
  if (valor !== undefined) a.valor = valor;
  else if (status === "CONCLUIDO" && a.valor === null) a.valor = db.precos.find((p) => p.tipo === a.tipo)?.valor ?? null;
  if (antes.valor !== a.valor || (antes.status === "CONCLUIDO") !== (a.status === "CONCLUIDO")) {
    const txt = (s: string, v: number | null) => `${s} · R$ ${v === null ? "-" : v.toFixed(2)}`;
    registrar("ALTEROU", "agendamento", id, txt(antes.status, antes.valor), txt(a.status, a.valor));
  }
  // banho concluído atualiza o "último banho" do pet (mesma regra da API)
  if (status === "CONCLUIDO" && (a.tipo === "BANHO" || a.tipo === "BANHO_E_TOSA")) {
    const pet = db.pets.find((p) => p.id === a.petId)!;
    pet.ultimoBanho = a.data;
  }
  return comNomes(a);
}

/* ---------- lembretes ---------- */

export async function listarLembretes(data: string): Promise<Lembrete[]> {
  if (!modoDemonstracao) return chamar(`/lembretes?data=${data}`);
  await espera();
  return calcularLembretes(data);
}

export async function marcarEnviado(chave: string): Promise<void> {
  if (!modoDemonstracao) return chamar(`/lembretes/enviados`, { method: "POST", body: JSON.stringify({ chave }) });
  db.enviados.add(chave);
}

/* ---------- financeiro ---------- */

export type DadosLancamento = Pick<Lancamento, "data" | "categoriaId" | "descricao" | "valor" | "recorrente">;

export async function listarCategorias(): Promise<Categoria[]> {
  if (!modoDemonstracao) return chamar(`/financeiro/categorias`);
  return [...db.categorias].sort((a, b) => a.tipo.localeCompare(b.tipo) || a.nome.localeCompare(b.nome));
}

export async function criarCategoria(nome: string, tipo: TipoLancamento): Promise<Categoria> {
  if (!modoDemonstracao) return chamar(`/financeiro/categorias`, { method: "POST", body: JSON.stringify({ nome, tipo }) });
  const limpo = nome.trim();
  if (db.categorias.some((c) => c.tipo === tipo && c.nome.toLowerCase() === limpo.toLowerCase())) {
    throw new Error("Já existe uma categoria com esse nome.");
  }
  const c = { id: novoId(), nome: limpo, tipo };
  db.categorias.push(c);
  registrar("CRIOU", "categoria", c.id, null, `${tipo} · ${limpo}`);
  return c;
}

export async function renomearCategoria(id: number, nome: string): Promise<Categoria> {
  if (!modoDemonstracao) return chamar(`/financeiro/categorias/${id}`, { method: "PUT", body: JSON.stringify({ nome }) });
  const c = db.categorias.find((x) => x.id === id)!;
  const limpo = nome.trim();
  if (db.categorias.some((x) => x.id !== id && x.tipo === c.tipo && x.nome.toLowerCase() === limpo.toLowerCase())) {
    throw new Error("Já existe uma categoria com esse nome.");
  }
  registrar("ALTEROU", "categoria", id, `${c.tipo} · ${c.nome}`, `${c.tipo} · ${limpo}`);
  c.nome = limpo;
  db.lancamentos.filter((l) => l.categoriaId === id).forEach((l) => (l.categoriaNome = limpo));
  return { ...c };
}

/** Só apaga categoria sem lançamentos (mesma regra da API). */
export async function excluirCategoria(id: number): Promise<void> {
  if (!modoDemonstracao) return chamar(`/financeiro/categorias/${id}`, { method: "DELETE" });
  const emUso = db.lancamentos.filter((l) => l.categoriaId === id).length;
  if (emUso > 0) throw new Error(`Essa categoria tem ${emUso} lançamento(s). Mude-os para outra categoria antes de excluir.`);
  const c = db.categorias.find((x) => x.id === id)!;
  registrar("EXCLUIU", "categoria", id, `${c.tipo} · ${c.nome}`, null);
  db.categorias.splice(db.categorias.indexOf(c), 1);
}

/** Lançamentos do mês (yyyy-MM). Abrir um mês lança sozinho as despesas que repetem todo mês. */
export async function listarLancamentos(mes: string): Promise<Lancamento[]> {
  if (!modoDemonstracao) return chamar(`/financeiro/lancamentos?mes=${mes}`);
  await espera();
  garantirRecorrentes(mes);
  return db.lancamentos
    .filter((l) => l.data.startsWith(mes))
    .sort((a, b) => a.data.localeCompare(b.data) || a.id - b.id)
    .map(paraDto);
}

/** Tira o vínculo interno da recorrência (a API também não devolve). */
function paraDto(l: LancamentoDemo): Lancamento {
  const dto: Partial<LancamentoDemo> = { ...l };
  delete dto.copiadoDe;
  return dto as Lancamento;
}

function lancamentoDemo(dados: DadosLancamento): Omit<Lancamento, "id"> {
  const c = db.categorias.find((x) => x.id === dados.categoriaId);
  if (!c) throw new Error("Categoria não encontrada");
  return { ...dados, descricao: dados.descricao.trim(), tipo: c.tipo, categoriaNome: c.nome, recorrente: dados.recorrente && podeRepetir(c.tipo) };
}

export async function criarLancamento(dados: DadosLancamento): Promise<Lancamento> {
  if (!modoDemonstracao) return chamar(`/financeiro/lancamentos`, { method: "POST", body: JSON.stringify(dados) });
  const l = { ...lancamentoDemo(dados), id: novoId() };
  db.lancamentos.push({ ...l, copiadoDe: null });
  registrar("CRIOU", "lancamento", l.id, null, descrever(l));
  return l;
}

export async function alterarLancamento(id: number, dados: DadosLancamento): Promise<Lancamento> {
  if (!modoDemonstracao) return chamar(`/financeiro/lancamentos/${id}`, { method: "PUT", body: JSON.stringify(dados) });
  const l = db.lancamentos.find((x) => x.id === id)!;
  const antes = descrever(l);
  Object.assign(l, lancamentoDemo(dados));
  registrar("ALTEROU", "lancamento", id, antes, descrever(l));
  return paraDto(l);
}

export async function excluirLancamento(id: number): Promise<void> {
  if (!modoDemonstracao) return chamar(`/financeiro/lancamentos/${id}`, { method: "DELETE" });
  const l = db.lancamentos.find((x) => x.id === id)!;
  registrar("EXCLUIU", "lancamento", id, descrever(l), null);
  // excluir uma cópia encerra a recorrência (mesma regra da API)
  const origem = db.lancamentos.find((x) => x.id === l.copiadoDe);
  if (origem) origem.recorrente = false;
  db.lancamentos.forEach((x) => x.copiadoDe === id && (x.copiadoDe = null));
  db.lancamentos = db.lancamentos.filter((x) => x.id !== id);
}

/** Atendimentos concluídos do mês (receitas de serviço). */
export async function listarAtendimentos(mes: string): Promise<Agendamento[]> {
  if (!modoDemonstracao) return chamar(`/financeiro/atendimentos?mes=${mes}`);
  return db.agendamentos
    .filter((a) => a.status === "CONCLUIDO" && a.data.startsWith(mes))
    .sort((a, b) => a.data.localeCompare(b.data) || a.hora.localeCompare(b.hora))
    .map(comNomes);
}

export async function buscarResumo(mes: string): Promise<Resumo> {
  if (!modoDemonstracao) return chamar(`/financeiro/resumo?mes=${mes}`);
  await espera();
  garantirRecorrentes(mes);
  const ultimosMeses = [-5, -4, -3, -2, -1, 0].map((n) => calcularResumoMes(somarMeses(mes, n)));
  return { atual: ultimosMeses[5], anterior: ultimosMeses[4], ultimosMeses };
}

export async function listarPrecos(): Promise<PrecoServico[]> {
  if (!modoDemonstracao) return chamar(`/precos`);
  return db.precos.map((p) => ({ ...p }));
}

export async function definirPreco(tipo: TipoServico, valor: number): Promise<PrecoServico> {
  if (!modoDemonstracao) return chamar(`/precos/${tipo}`, { method: "PUT", body: JSON.stringify({ valor }) });
  const p = db.precos.find((x) => x.tipo === tipo);
  const antes = p?.valor ?? null;
  if (antes === valor) return { tipo, valor };
  if (p) p.valor = valor;
  else db.precos.push({ tipo, valor });
  registrar("ALTEROU", "preco", tipo, antes === null ? null : `R$ ${antes.toFixed(2)}`, `R$ ${valor.toFixed(2)}`);
  return { tipo, valor };
}

/** Últimas 100 alterações do financeiro, da mais nova para a mais antiga. Só leitura. */
export async function listarHistorico(): Promise<RegistroHistorico[]> {
  if (!modoDemonstracao) return chamar(`/financeiro/historico`);
  return db.historico.slice(0, 100);
}
