/**
 * Cliente da API do pet shop (Spring Boot). É como um service com HttpClient no Angular:
 * as telas só chamam estas funções e não sabem se os dados vêm da API ou da demonstração.
 */
import { loja } from "./loja";
import {
  calcularLembretes, calcularResumoMes, comNomes, db, descrever, garantirRecorrentes, novoId, registrar, tutorCompleto, type LancamentoDemo,
} from "./demo";
import { hoje, somarDias, somarMeses } from "./datas";
import { podeRepetir } from "./tipos";
import type {
  AplicacaoVacina, Agendamento, Aviso, Campanha, Eu, UsuarioPainel, Papel, HistoricoCliente, ConfirmacaoOnline, PedidoOnline, ServicoPublico, Categoria, ConfigLoja, MensagemWhatsApp, ResultadoEnvio, SituacaoWhatsApp, TipoMensagem, Lancamento, Lembrete, Pet, PrecoServico, RegistroHistorico, Resumo, StatusAgendamento, TipoLancamento,
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
  if (res.status === 429) throw new Error("Muitas tentativas erradas. Espere alguns minutos e tente de novo.");
  if (!res.ok) throw new Error("Não foi possível conectar ao sistema.");
  sessionStorage.setItem(CHAVE_SESSAO, token);
}

export function sair() {
  euCache = null;
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
  if (res.status === 403) throw new Error("Seu usuário não tem permissão para isso. Peça ao dono da loja.");
  if (!res.ok) {
    const corpo = await res.json().catch(() => null);
    const campos = corpo?.campos ? Object.values(corpo.campos as Record<string, string>) : [];
    throw new Error(campos.length ? campos.join(" · ") : (corpo?.detail ?? "Não foi possível concluir. Tente novamente."));
  }
  return res.status === 204 ? (undefined as T) : res.json();
}

const espera = () => new Promise((r) => setTimeout(r, 150));

/* ---------- clientes e pets ---------- */

export type NovoTutor = Omit<Tutor, "id" | "pets" | "promocoesDesde" | "saiuDasPromocoesEm">;
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

export type NovoAgendamento = Pick<Agendamento, "petId" | "data" | "hora" | "tipo" | "descricao" | "buscaMoto" | "enderecoBusca">;

export async function listarAgenda(data: string): Promise<Agendamento[]> {
  if (!modoDemonstracao) return chamar(`/agenda?data=${data}`);
  await espera();
  return db.agendamentos.filter((a) => a.data === data).sort((a, b) => a.hora.localeCompare(b.hora)).map(comNomes);
}

export async function criarAgendamento(dados: NovoAgendamento): Promise<Agendamento> {
  if (!modoDemonstracao) return chamar(`/agenda`, { method: "POST", body: JSON.stringify(dados) });
  garantirIntervalo(dados.data, dados.hora, null);
  if (dados.buscaMoto && !dados.enderecoBusca.trim()) throw new Error("Informe o endereço para a busca de moto");
  const a = { ...dados, id: novoId(), status: "AGUARDANDO" as StatusAgendamento, valor: null, canal: "PAINEL" as const };
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

/** Mesma regra da API: pelo menos o intervalo da loja (tela Configurações) entre um atendimento e outro, de qualquer cliente. */
function garantirIntervalo(data: string, hora: string, ignorar: number | null) {
  const minutos = (h: string) => Number(h.slice(0, 2)) * 60 + Number(h.slice(3, 5));
  const perto = db.agendamentos.find(
    (x) => x.id !== ignorar && x.data === data && x.status !== "CANCELADO" && Math.abs(minutos(x.hora) - minutos(hora)) < db.config.intervaloAgendaMinutos,
  );
  if (perto) {
    throw new Error(`Muito perto do atendimento das ${perto.hora}. Deixe pelo menos ${db.config.intervaloAgendaMinutos} minutos entre um atendimento e outro.`);
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
  if (antes.status !== "CONCLUIDO" && a.status === "CONCLUIDO") pedirAvaliacaoDemo(a.petId);
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

/* ---------- configurações da loja ---------- */

export async function buscarConfig(): Promise<ConfigLoja> {
  if (!modoDemonstracao) return chamar(`/config`);
  return { ...db.config };
}

export async function salvarConfig(c: ConfigLoja): Promise<ConfigLoja> {
  if (!modoDemonstracao) return chamar(`/config`, { method: "PUT", body: JSON.stringify(c) });
  // mesmas regras principais da API (demonstração)
  if (c.fecha <= c.abre || (c.sabadoAbre && c.sabadoFecha && c.sabadoFecha <= c.sabadoAbre)) {
    throw new Error("O horário de fechar precisa ser depois do de abrir");
  }
  if (c.intervaloAgendaMinutos < 10 || c.intervaloAgendaMinutos > 240) throw new Error("Use de 10 a 240 minutos");
  if (c.linkAvaliacao && !c.linkAvaliacao.startsWith("https://")) throw new Error("O link precisa começar com https://");
  registrar("ALTEROU", "configuracoes", 1, null, "configurações da loja");
  db.config = { ...c, whatsapp: c.whatsapp.replace(/\D/g, ""), instagram: c.instagram.replace(/^@/, "") };
  return { ...db.config };
}

/* ---------- WhatsApp ---------- */

export async function buscarWhatsapp(): Promise<SituacaoWhatsApp> {
  if (!modoDemonstracao) return chamar(`/whatsapp`);
  return { ...db.whatsapp, temLinkAvaliacao: !!db.config.linkAvaliacao };
}

export async function salvarWhatsapp(p: Omit<SituacaoWhatsApp, "oficial" | "temLinkAvaliacao">): Promise<SituacaoWhatsApp> {
  if (!modoDemonstracao) return chamar(`/whatsapp`, { method: "PUT", body: JSON.stringify(p) });
  db.whatsapp = { ...db.whatsapp, ...p };
  return { ...db.whatsapp };
}

export async function listarMensagens(): Promise<MensagemWhatsApp[]> {
  if (!modoDemonstracao) return chamar(`/whatsapp/mensagens`);
  return db.mensagens.slice(0, 100).map(({ chave: _c, ...m }) => (void _c, m));
}

/** Demonstração: sempre simulada (nada sai), igual à API sem a Cloud API configurada. */
function simular(para: string, tipo: TipoMensagem, texto: string, chave: string | null = null): MensagemWhatsApp {
  const d = para.replace(/\D/g, "");
  const m = { id: novoId(), para: d.length === 10 || d.length === 11 ? `55${d}` : d, tipo, texto, status: "SIMULADA" as const,
    erro: null, criadoEm: new Date().toISOString(), chave };
  db.mensagens.unshift(m);
  return m;
}

export async function enviarTesteWhatsapp(): Promise<MensagemWhatsApp> {
  if (!modoDemonstracao) return chamar(`/whatsapp/teste`, { method: "POST" });
  return simular(db.config.whatsapp, "TESTE", `Teste do sistema ${db.config.nome}: o WhatsApp está funcionando.`);
}

/** Roda agora o envio das 18h (lembretes de amanhã); o mesmo lembrete não sai duas vezes. */
export async function lembretesAgora(): Promise<ResultadoEnvio> {
  if (!modoDemonstracao) return chamar(`/whatsapp/lembretes-agora`, { method: "POST" });
  const r = { enviadas: 0, simuladas: 0, falhas: 0, jaFeitas: 0 };
  for (const l of calcularLembretes(somarDias(hoje(), 1))) {
    if (l.enviado || db.mensagens.some((m) => m.chave === l.chave)) r.jaFeitas++;
    else {
      simular(l.whatsapp, "LEMBRETE", l.mensagem, l.chave);
      r.simuladas++;
    }
  }
  return r;
}

/* ---------- avisos do sistema ---------- */

/** O que precisa de atenção (limite de plano, espaço, falhas, segurança) e o que fazer. */
export async function listarAvisos(): Promise<Aviso[]> {
  if (!modoDemonstracao) return chamar(`/avisos`);
  return db.whatsapp.lembretesAutomaticos
    ? [{ nivel: "ATENCAO", area: "WhatsApp", titulo: "Lembretes automáticos ligados, mas nada está saindo",
        detalhe: "O WhatsApp está no modo simulado (sem a API oficial). Os lembretes ficam só registrados.",
        acao: "Mande por clique na tela WhatsApp, ou configure a WhatsApp Cloud API no servidor (README, seção 4)." }]
    : [];
}

/* ---------- agendamento online (público, sem login) ---------- */

async function publico<T>(caminho: string, init: RequestInit = {}): Promise<T> {
  const res = await fetch(`${API_URL}/api/publico/lojas/${loja.slug}${caminho}`, {
    ...init,
    headers: { "Content-Type": "application/json", ...init.headers },
    cache: "no-store",
  });
  if (!res.ok) {
    const corpo = await res.json().catch(() => null);
    const campos = corpo?.campos ? Object.values(corpo.campos as Record<string, string>) : [];
    throw new Error(campos.length ? campos.join(" · ") : (corpo?.detail ?? "Não foi possível concluir. Tente de novo ou chame no WhatsApp."));
  }
  return res.json();
}

/** Dados públicos da loja (nome, WhatsApp...) para as páginas sem login. */
export async function buscarLojaPublica(): Promise<ConfigLoja> {
  if (!modoDemonstracao) return publico(``);
  return { ...db.config };
}

export async function listarServicosPublicos(): Promise<ServicoPublico[]> {
  if (!modoDemonstracao) return publico(`/servicos`);
  return db.precos.map((p) => ({ tipo: p.tipo, nome: NOMES_SERVICO_PUBLICO[p.tipo], preco: p.valor }));
}

const NOMES_SERVICO_PUBLICO: Record<TipoServico, string> = {
  BANHO: "Banho", TOSA: "Tosa", BANHO_E_TOSA: "Banho e tosa", VACINA: "Vacina", CONSULTA: "Consulta",
};

/** Mesma regra da API: funcionamento da loja, de intervalo em intervalo, longe de outros atendimentos, 1 h de antecedência. */
export async function horariosLivres(data: string): Promise<string[]> {
  if (!modoDemonstracao) return publico(`/horarios?data=${data}`);
  const c = db.config;
  const h = hoje();
  if (data < h || data > somarDias(h, 60)) return [];
  const dia = new Date(`${data}T12:00:00`).getDay();
  const [abre, fecha] = dia === 0 ? [null, null] : dia === 6 ? [c.sabadoAbre, c.sabadoFecha] : [c.abre, c.fecha];
  if (!abre || !fecha) return [];
  const min = (x: string) => Number(x.slice(0, 2)) * 60 + Number(x.slice(3, 5));
  const agora = new Date().toLocaleTimeString("en-GB", { timeZone: "America/Sao_Paulo", hour: "2-digit", minute: "2-digit" });
  const minimo = data === h ? min(agora) + 60 : 0;
  const ativos = db.agendamentos.filter((a) => a.data === data && a.status !== "CANCELADO");
  const livres: string[] = [];
  for (let m = min(abre); m + c.intervaloAgendaMinutos <= min(fecha); m += c.intervaloAgendaMinutos) {
    if (m < minimo || ativos.some((a) => Math.abs(min(a.hora) - m) < c.intervaloAgendaMinutos)) continue;
    livres.push(`${String(Math.floor(m / 60)).padStart(2, "0")}:${String(m % 60).padStart(2, "0")}`);
  }
  return livres;
}

export async function agendarOnline(p: PedidoOnline): Promise<ConfirmacaoOnline> {
  if (!modoDemonstracao) return publico(`/agendamentos`, { method: "POST", body: JSON.stringify(p) });
  if (p.site) throw new Error("Não foi possível concluir o agendamento.");
  if (!p.aceiteDados) throw new Error("É preciso aceitar o uso dos dados para agendar");
  if (p.buscaMoto && !p.enderecoBusca.trim()) throw new Error("Informe o endereço para a busca de moto");
  if (!(await horariosLivres(p.data)).includes(p.hora)) throw new Error("Esse horário não está mais disponível. Escolha outro.");
  const d = p.whatsapp.replace(/\D/g, "");
  const local = d.startsWith("55") && d.length >= 12 ? d.slice(2) : d;
  let tutor = db.tutores.find((t) => !t.removido && t.whatsapp.replace(/\D/g, "") === local);
  if (!tutor) {
    const origens: Record<string, string> = { instagram: "Instagram", google: "Google", whatsapp: "WhatsApp", indicacao: "Indicação" };
    tutor = { id: novoId(), nome: p.nome.trim(), whatsapp: local, endereco: "", cpf: "", origem: origens[p.origem] ?? "Site" };
    db.tutores.push(tutor);
  }
  const tutorId = tutor.id;
  let pet = db.pets.find((x) => x.tutorId === tutorId && x.nome.toLowerCase() === p.petNome.trim().toLowerCase());
  if (!pet) {
    pet = { id: novoId(), tutorId, nome: p.petNome.trim(), especie: p.especie, raca: "", pesoKg: null, observacoes: "", intervaloBanhoDias: null, ultimoBanho: null };
    db.pets.push(pet);
  }
  const a = { id: novoId(), petId: pet.id, data: p.data, hora: p.hora, tipo: p.tipo, descricao: p.observacao, status: "AGUARDANDO" as StatusAgendamento,
    valor: null, canal: "ONLINE" as const, buscaMoto: p.buscaMoto, enderecoBusca: p.buscaMoto ? p.enderecoBusca : "" };
  db.agendamentos.push(a);
  const servico = NOMES_SERVICO_PUBLICO[p.tipo].toLowerCase();
  const [, mm, dd] = p.data.split("-");
  const mensagem = `Olá, ${p.nome.trim().split(/\s+/)[0]}! Aqui é da ${db.config.nome}. Recebemos seu agendamento: ${servico} do ${pet.nome} em ${dd}/${mm} às ${p.hora}${p.buscaMoto ? " (com busca de moto)" : ""}. Confirmamos pelo WhatsApp em breve.`;
  simular(local, "CONFIRMACAO", mensagem);
  return { data: `${dd}/${mm}`, hora: p.hora, servico, petNome: pet.nome, buscaMoto: p.buscaMoto, mensagem };
}

/* ---------- promoções e campanhas (LGPD: só quem aceitou) ---------- */

/** Registra o aceite de promoções feito pela loja (cliente pediu pessoalmente) ou tira da lista. */
export async function promocoesCliente(id: number, aceita: boolean): Promise<Tutor> {
  if (!modoDemonstracao) return chamar(`/tutores/${id}/promocoes`, { method: "PUT", body: JSON.stringify({ aceita }) });
  const t = db.tutores.find((x) => x.id === id && !x.removido)!;
  const agora = new Date().toISOString();
  if (aceita) Object.assign(t, { promocoesDesde: t.promocoesDesde ?? agora, saiuDasPromocoesEm: null, token: t.token ?? crypto.randomUUID().replace(/-/g, "") });
  else Object.assign(t, { saiuDasPromocoesEm: t.promocoesDesde ? agora : t.saiuDasPromocoesEm, promocoesDesde: null });
  registrar(aceita ? "CRIOU" : "EXCLUIU", "consentimento-promocoes", id, null, `cliente #${id} ${aceita ? "aceitou" : "saiu das"} promoções`);
  return tutorCompleto(t);
}

export async function publicoCampanha(): Promise<number> {
  if (!modoDemonstracao) return (await chamar<{ clientes: number }>(`/campanhas/publico`)).clientes;
  return db.tutores.filter((t) => !t.removido && t.promocoesDesde && t.whatsapp).length;
}

export async function listarCampanhas(): Promise<Campanha[]> {
  if (!modoDemonstracao) return chamar(`/campanhas`);
  return db.campanhas.map(({ pendentes: _p, ...c }) => (void _p, c));
}

/** Cria a campanha para quem aceitou e envia (demonstração: simulada, sem limite diário). */
export async function criarCampanha(titulo: string, texto: string, template: string): Promise<Campanha> {
  if (!modoDemonstracao) return chamar(`/campanhas`, { method: "POST", body: JSON.stringify({ titulo, texto, template }) });
  if (!/^[a-z0-9_]{1,80}$/.test(template)) throw new Error("Nome do modelo: só letras minúsculas, números e _");
  const publico = db.tutores.filter((t) => !t.removido && t.promocoesDesde && t.whatsapp);
  if (publico.length === 0) throw new Error("Nenhum cliente aceitou receber promoções ainda. A campanha só vai para quem aceitou (LGPD).");
  for (const t of publico) {
    simular(t.whatsapp, "CAMPANHA", `${texto}\n\nNão quer mais receber promoções? ${window.location.origin}/sair?t=${t.token}`);
  }
  const c = { id: novoId(), titulo, texto, status: "CONCLUIDA" as const, criadaEm: new Date().toISOString(), pendentes: [],
    envios: { PENDENTE: 0, ENVIADA: 0, SIMULADA: publico.length, FALHOU: 0, PULADO: 0 } };
  db.campanhas.unshift(c);
  registrar("CRIOU", "campanha", c.id, null, `${titulo} · ${publico.length} clientes`);
  return c;
}

/** Link "não quero mais receber" (página /sair). */
export async function descadastrar(token: string): Promise<void> {
  if (!modoDemonstracao) {
    await fetch(`${API_URL}/api/publico/descadastro`, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ token }) });
    return;
  }
  const t = db.tutores.find((x) => x.token === token);
  if (t?.promocoesDesde) Object.assign(t, { promocoesDesde: null, saiuDasPromocoesEm: new Date().toISOString() });
}

/** Demonstração: pedido de avaliação no Google depois do atendimento, 1 vez a cada 90 dias por cliente. */
function pedirAvaliacaoDemo(petId: number) {
  const link = db.config.linkAvaliacao;
  if (!db.whatsapp.pedirAvaliacao || !link) return;
  const pet = db.pets.find((p) => p.id === petId)!;
  const t = db.tutores.find((x) => x.id === pet.tutorId)!;
  const para = t.whatsapp.replace(/\D/g, "");
  const noventaDias = Date.now() - 90 * 86_400_000;
  if (db.mensagens.some((m) => m.tipo === "AVALIACAO" && m.para.endsWith(para) && Date.parse(m.criadoEm) > noventaDias)) return;
  simular(t.whatsapp, "AVALIACAO", `Olá, ${t.nome.split(" ")[0]}! Aqui é da ${db.config.nome}. Obrigado por trazer o ${pet.nome}! Se puder, avalie nosso atendimento no Google: ${link}`);
}

/* ---------- histórico do cliente ---------- */

export async function historicoCliente(tutorId: number): Promise<HistoricoCliente> {
  if (!modoDemonstracao) return chamar(`/agenda/cliente/${tutorId}`);
  const pets = db.pets.filter((p) => p.tutorId === tutorId).map((p) => p.id);
  const lista = db.agendamentos
    .filter((a) => pets.includes(a.petId))
    .sort((a, b) => (b.data + b.hora).localeCompare(a.data + a.hora))
    .slice(0, 200)
    .map(comNomes);
  const concluidos = lista.filter((a) => a.status === "CONCLUIDO");
  return {
    totalGasto: concluidos.reduce((t, a) => t + Math.round((a.valor ?? 0) * 100), 0) / 100,
    atendimentosConcluidos: concluidos.length,
    ultimoAtendimento: concluidos[0]?.data ?? null,
    atendimentos: lista,
  };
}

/* ---------- usuários (logins por pessoa) ---------- */

let euCache: Promise<Eu> | null = null;

/** Quem está logado (guardado enquanto a aba estiver aberta; sair() limpa). Demonstração: sempre o dono. */
export function quemSouEu(): Promise<Eu> {
  if (modoDemonstracao) return Promise.resolve({ login: "demo", nome: "Demonstração", papel: "DONO", contaPrincipal: true });
  if (!euCache) euCache = chamar<Eu>(`/usuarios/eu`).catch((e) => { euCache = null; throw e; });
  return euCache;
}

const usuariosDemo: UsuarioPainel[] = [];

export async function listarUsuarios(): Promise<UsuarioPainel[]> {
  if (!modoDemonstracao) return chamar(`/usuarios`);
  return usuariosDemo.map((u) => ({ ...u }));
}

export async function criarUsuario(nome: string, login: string, senha: string, papel: Papel): Promise<UsuarioPainel> {
  if (!modoDemonstracao) return chamar(`/usuarios`, { method: "POST", body: JSON.stringify({ nome, login, senha, papel }) });
  if (senha.length < 10) throw new Error("A senha precisa ter de 10 a 72 caracteres");
  const l = login.trim().toLowerCase();
  if (usuariosDemo.some((u) => u.login === l)) throw new Error("Esse login já existe. Escolha outro.");
  const u = { id: novoId(), nome: nome.trim(), login: l, papel, ativo: true };
  usuariosDemo.push(u);
  return { ...u };
}

export async function alterarUsuario(id: number, nome: string, papel: Papel, ativo: boolean): Promise<UsuarioPainel> {
  if (!modoDemonstracao) return chamar(`/usuarios/${id}`, { method: "PUT", body: JSON.stringify({ nome, papel, ativo }) });
  const u = usuariosDemo.find((x) => x.id === id)!;
  Object.assign(u, { nome, papel, ativo });
  return { ...u };
}

/** O dono redefine a senha de quem esqueceu. */
export async function redefinirSenha(id: number, senha: string): Promise<void> {
  if (!modoDemonstracao) return chamar(`/usuarios/${id}/senha`, { method: "PUT", body: JSON.stringify({ senha }) });
  if (senha.length < 10) throw new Error("A senha precisa ter de 10 a 72 caracteres");
}

/** Troca a própria senha; o login guardado na aba passa a usar a nova. */
export async function trocarMinhaSenha(atual: string, nova: string): Promise<void> {
  if (modoDemonstracao) throw new Error("No modo demonstração não há senha para trocar.");
  await chamar(`/usuarios/eu/senha`, { method: "PUT", body: JSON.stringify({ atual, nova }) });
  const login = atob(credencial() ?? "").split(":")[0];
  sessionStorage.setItem(CHAVE_SESSAO, btoa(`${login}:${nova}`));
}
