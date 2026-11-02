/**
 * Dados de demonstração em memória, usados quando NEXT_PUBLIC_API_URL não está definido.
 * As regras (lembretes, último banho) são as mesmas da API em Spring Boot.
 */
import { formatar, hoje, mesAtual, mesmoDiaNoProximoMes, proximoBanho, somarDias, somarMeses } from "./datas";
import {
  NOMES_SERVICO,
  type AplicacaoVacina, type Agendamento, type Categoria, type Lancamento, type Lembrete, type Pet, type PrecoServico,
  type RegistroHistorico, type ResumoMes, type TipoLancamento, type TipoServico, type Tutor,
} from "./tipos";

const H = hoje();
const amanha = somarDias(H, 1);

let seq = 5000; // acima dos ids de exemplo
const novoId = () => ++seq;

/* ---------- financeiro de exemplo ---------- */

const CATEGORIAS: Categoria[] = (
  [
    ["FIXO", "Aluguel"], ["FIXO", "Energia"], ["FIXO", "Água"], ["FIXO", "Funcionários"], ["FIXO", "Impostos"], ["FIXO", "Outros"],
    ["VARIAVEL", "Freelancers"], ["VARIAVEL", "Shampoo e produtos de banho"], ["VARIAVEL", "Gasolina da moto"], ["VARIAVEL", "Outros"],
    ["EVENTUAL", "Equipamentos"], ["EVENTUAL", "Manutenção"], ["EVENTUAL", "Conserto da moto"], ["EVENTUAL", "Reforma"], ["EVENTUAL", "Outros"],
    ["RECEITA", "Ração"], ["RECEITA", "Produtos"], ["RECEITA", "Outros"],
  ] as [TipoLancamento, string][]
).map(([tipo, nome], i) => ({ id: 500 + i, tipo, nome }));

/** Lançamento guardado na demonstração; copiadoDe liga a cópia recorrente ao mês anterior (como na API). */
export type LancamentoDemo = Lancamento & { copiadoDe: number | null };

function lancamentosDeExemplo(): LancamentoDemo[] {
  const lista: LancamentoDemo[] = [];
  let id = 900;
  const add = (mes: string, dia: string, tipo: TipoLancamento, nome: string, valor: number, descricao = "", recorrente = false, copiadoDe: number | null = null) => {
    const c = CATEGORIAS.find((x) => x.tipo === tipo && x.nome === nome)!;
    lista.push({ id: ++id, data: `${mes}-${dia}`, tipo, categoriaId: c.id, categoriaNome: c.nome, descricao, valor, recorrente, copiadoDe });
    return id;
  };
  const M = mesAtual();
  // custos fixos recorrentes há 3 meses; a energia e a água mudam todo mês
  const fixos: [string, string, string, number[]][] = [
    ["05", "Aluguel", "Ponto da loja", [1200, 1200, 1200]],
    ["05", "Funcionários", "Salário do banhista", [1800, 1800, 1800]],
    ["10", "Energia", "Conta de luz", [298.7, 341.25, 316.4]],
    ["10", "Água", "", [92, 95.5, 98.3]],
    ["20", "Impostos", "DAS do MEI", [75.6, 75.6, 75.6]],
  ];
  for (const [dia, nome, desc, valores] of fixos) {
    let anterior: number | null = null;
    valores.forEach((v, i) => (anterior = add(somarMeses(M, i - 2), dia, "FIXO", nome, v, desc, true, anterior)));
  }
  const variaveis: [number, string, string, string, number][] = [
    [-2, "08", "Shampoo e produtos de banho", "Shampoo e condicionador", 165],
    [-2, "15", "Gasolina da moto", "Busca e entrega", 110],
    [-1, "07", "Shampoo e produtos de banho", "Shampoo, perfume e laços", 182.4],
    [-1, "14", "Gasolina da moto", "Busca e entrega", 128.9],
    [-1, "21", "Freelancers", "Tosador no sábado", 150],
    [0, "03", "Shampoo e produtos de banho", "Shampoo hipoalergênico", 210.9],
    [0, "12", "Gasolina da moto", "Busca e entrega", 145.5],
    [0, "18", "Freelancers", "Tosador no sábado", 300],
  ];
  for (const [m, dia, nome, desc, v] of variaveis) add(somarMeses(M, m), dia, "VARIAVEL", nome, v, desc);
  add(somarMeses(M, -1), "24", "EVENTUAL", "Equipamentos", 890, "Secador novo");
  add(M, "06", "EVENTUAL", "Conserto da moto", 230, "Troca da corrente");
  add(somarMeses(M, -2), "16", "RECEITA", "Ração", 360, "Ração 15 kg");
  add(somarMeses(M, -1), "11", "RECEITA", "Ração", 420, "Ração 15 kg (2 sacos)");
  add(M, "09", "RECEITA", "Ração", 380, "Ração 15 kg");
  add(M, "13", "RECEITA", "Produtos", 95, "Coleira e petisco");
  return lista;
}

/** Atendimentos concluídos desde o 1º dia de 2 meses atrás (sem domingo), mesmo período dos custos de exemplo. */
function historicoDeAtendimentos(): Omit<Agendamento, "petNome" | "tutorNome">[] {
  const inicio = `${somarMeses(mesAtual(), -2)}-01`;
  const ciclo: [number, TipoServico, number][] = [[21, "BANHO", 50], [11, "BANHO_E_TOSA", 90], [31, "TOSA", 60], [12, "BANHO", 50], [21, "VACINA", 80]];
  const lista: Omit<Agendamento, "petNome" | "tutorNome">[] = [];
  for (let d = 1; somarDias(H, -d) >= inicio; d++) {
    const data = somarDias(H, -d);
    if (new Date(`${data}T12:00:00`).getDay() === 0) continue;
    for (let i = 0; i < 2 + (d % 2); i++) {
      const [petId, tipo, valor] = ciclo[(d + i) % ciclo.length];
      lista.push({ id: 1000 + d * 10 + i, petId, data, hora: `${String(8 + i * 2).padStart(2, "0")}:00`, tipo, descricao: NOMES_SERVICO[tipo], status: "CONCLUIDO", valor });
    }
  }
  return lista;
}

export const db = {
  tutores: [
    { id: 1, nome: "Felipe Santos", whatsapp: "(62) 99999-0000", endereco: "Rua das Flores, 120 · Centro", cpf: "", origem: "Instagram" },
    { id: 2, nome: "Mariana Costa", whatsapp: "(62) 98888-1111", endereco: "Av. Principal, 45", cpf: "", origem: "Google" },
    { id: 3, nome: "Ricardo Alves", whatsapp: "(62) 97777-2222", endereco: "Rua Nova, 300", cpf: "", origem: "Indicação" },
  ] as (Omit<Tutor, "pets"> & { removido?: boolean })[],
  pets: [
    { id: 11, tutorId: 1, nome: "Thor", especie: "Cão", raca: "Golden Retriever", pesoKg: 31, observacoes: "Agitado no secador. Usar shampoo hipoalergênico.", intervaloBanhoDias: 15, ultimoBanho: somarDias(H, -14) },
    { id: 12, tutorId: 1, nome: "Mel", especie: "Gato", raca: "SRD", pesoKg: 4, observacoes: "Arisca com estranhos. Atender em sala separada.", intervaloBanhoDias: 30, ultimoBanho: somarDias(H, -20) },
    { id: 21, tutorId: 2, nome: "Luna", especie: "Cão", raca: "Shih Tzu", pesoKg: 6, observacoes: "", intervaloBanhoDias: 15, ultimoBanho: somarDias(H, -5) },
    { id: 31, tutorId: 3, nome: "Bob", especie: "Cão", raca: "Vira-lata", pesoKg: 18, observacoes: "", intervaloBanhoDias: null, ultimoBanho: null },
  ] as Pet[],
  vacinas: [
    { id: 1, petId: 11, vacina: "V10", dose: "3ª dose", aplicadaEm: somarDias(H, -27), proximaEm: amanha },
    { id: 2, petId: 11, vacina: "Antirrábica", dose: "Anual", aplicadaEm: somarDias(H, -230), proximaEm: somarDias(H, 135) },
    { id: 3, petId: 11, vacina: "Gripe canina", dose: "Anual", aplicadaEm: somarDias(H, -410), proximaEm: somarDias(H, -45) },
    { id: 4, petId: 12, vacina: "V4 felina", dose: "Reforço anual", aplicadaEm: somarDias(H, -362), proximaEm: somarDias(H, 3) },
    { id: 5, petId: 31, vacina: "Antirrábica", dose: "Anual", aplicadaEm: somarDias(H, -364), proximaEm: amanha },
  ] as AplicacaoVacina[],
  agendamentos: [
    { id: 1, petId: 21, data: H, hora: "08:00", tipo: "BANHO", descricao: "Banho", status: "CONCLUIDO", valor: 50 },
    { id: 2, petId: 11, data: H, hora: "10:00", tipo: "BANHO_E_TOSA", descricao: "Banho + hidratação", status: "CONFIRMADO", valor: null },
    { id: 3, petId: 12, data: H, hora: "11:00", tipo: "VACINA", descricao: "V4 felina, reforço anual", status: "AGUARDANDO", valor: null },
    { id: 4, petId: 31, data: H, hora: "14:00", tipo: "CONSULTA", descricao: "Consulta", status: "AGUARDANDO", valor: null },
    { id: 5, petId: 21, data: amanha, hora: "10:30", tipo: "BANHO_E_TOSA", descricao: "Banho e tosa", status: "CONFIRMADO", valor: null },
    ...historicoDeAtendimentos(),
  ] as Omit<Agendamento, "petNome" | "tutorNome">[],
  enviados: new Set<string>(),
  /* financeiro (mesmos valores de exemplo da migration V3) */
  precos: [
    { tipo: "BANHO", valor: 50 }, { tipo: "TOSA", valor: 60 }, { tipo: "BANHO_E_TOSA", valor: 90 },
    { tipo: "VACINA", valor: 80 }, { tipo: "CONSULTA", valor: 120 },
  ] as PrecoServico[],
  categorias: CATEGORIAS,
  lancamentos: lancamentosDeExemplo(),
  historico: [] as RegistroHistorico[],
};

/** Mesmo histórico da API (AuditoriaService), em memória. */
export function registrar(acao: RegistroHistorico["acao"], entidade: string, id: number | string, antes: string | null, depois: string | null) {
  db.historico.unshift({ em: new Date().toISOString(), usuario: "demonstração", acao, entidade, entidadeId: String(id), antes, depois });
}

export function descrever(l: Lancamento): string {
  return `${l.data} · ${l.tipo} · ${l.categoriaNome} · R$ ${l.valor.toFixed(2)}${l.descricao ? ` · ${l.descricao}` : ""}${l.recorrente ? " · recorrente" : ""}`;
}

export function comNomes(a: Omit<Agendamento, "petNome" | "tutorNome">): Agendamento {
  const pet = db.pets.find((p) => p.id === a.petId)!;
  const tutor = db.tutores.find((t) => t.id === pet.tutorId)!;
  return { ...a, petNome: pet.nome, tutorNome: tutor.nome };
}

export function tutorCompleto(t: Omit<Tutor, "pets">): Tutor {
  return { ...t, pets: db.pets.filter((p) => p.tutorId === t.id) };
}

export { novoId };

/** Mesma regra do LembreteService da API: tudo que vence na data informada. */
export function calcularLembretes(data: string): Lembrete[] {
  const lista: Lembrete[] = [];
  const dono = (petId: number) => {
    const pet = db.pets.find((p) => p.id === petId)!;
    return { pet, tutor: db.tutores.find((t) => t.id === pet.tutorId)! };
  };
  const primeiro = (nome: string) => nome.split(" ")[0];

  for (const a of db.agendamentos.filter((a) => a.data === data && a.status !== "CANCELADO")) {
    const { pet, tutor } = dono(a.petId);
    lista.push({
      chave: `AGENDAMENTO:${a.id}:${data}`, tipo: "AGENDAMENTO", petId: pet.id, petNome: pet.nome, tutorNome: tutor.nome,
      whatsapp: tutor.whatsapp, data, enviado: false,
      mensagem: `Olá, ${primeiro(tutor.nome)}! Lembrete: amanhã (${formatar(data)}) às ${a.hora} o ${pet.nome} tem ${NOMES_SERVICO[a.tipo].toLowerCase()} marcado. Podemos confirmar?`,
    });
  }
  for (const v of db.vacinas.filter((v) => v.proximaEm === data)) {
    const { pet, tutor } = dono(v.petId);
    lista.push({
      chave: `VACINA:${v.id}:${data}`, tipo: "VACINA", petId: pet.id, petNome: pet.nome, tutorNome: tutor.nome,
      whatsapp: tutor.whatsapp, data, enviado: false,
      mensagem: `Olá, ${primeiro(tutor.nome)}! Amanhã (${formatar(data)}) é dia da vacina ${v.vacina} (${v.dose}) do ${pet.nome}. Vamos agendar o horário?`,
    });
  }
  for (const pet of db.pets.filter((p) => proximoBanho(p.ultimoBanho, p.intervaloBanhoDias) === data)) {
    const tutor = db.tutores.find((t) => t.id === pet.tutorId)!;
    lista.push({
      chave: `BANHO:${pet.id}:${data}`, tipo: "BANHO", petId: pet.id, petNome: pet.nome, tutorNome: tutor.nome,
      whatsapp: tutor.whatsapp, data, enviado: false,
      mensagem: `Olá, ${primeiro(tutor.nome)}! Amanhã completa ${pet.intervaloBanhoDias} dias do último banho do ${pet.nome}. Quer agendar o próximo?`,
    });
  }
  // cliente removido não tem WhatsApp: não recebe lembrete (mesma regra da API)
  return lista.filter((l) => l.whatsapp).map((l) => ({ ...l, enviado: db.enviados.has(l.chave) }));
}

/* ---------- financeiro: mesmas regras do FinanceiroService da API ---------- */

/** Copia os custos fixos recorrentes até o mês pedido (nunca para o futuro), uma vez só. */
export function garantirRecorrentes(mes: string) {
  const ate = mes > mesAtual() ? mesAtual() : mes;
  for (;;) {
    const pendentes = db.lancamentos.filter(
      (l) => l.recorrente && l.data < `${ate}-01` && !db.lancamentos.some((c) => c.copiadoDe === l.id),
    );
    if (pendentes.length === 0) return;
    for (const l of pendentes) {
      const copia = { ...l, id: novoId(), data: mesmoDiaNoProximoMes(l.data), copiadoDe: l.id };
      db.lancamentos.push(copia);
      registrar("COPIOU", "lancamento", copia.id, `recorrente #${l.id}`, descrever(copia));
    }
  }
}

/** Soma em centavos para não acumular erro de ponto flutuante. */
const somar = (valores: number[]) => valores.reduce((t, v) => t + Math.round(v * 100), 0);

export function calcularResumoMes(mes: string): ResumoMes {
  const lancs = db.lancamentos.filter((l) => l.data.startsWith(mes));
  const atends = db.agendamentos.filter((a) => a.status === "CONCLUIDO" && a.data.startsWith(mes));
  const total = (tipo: TipoLancamento) => somar(lancs.filter((l) => l.tipo === tipo).map((l) => l.valor));
  const fixo = total("FIXO");
  const variavel = total("VARIAVEL");
  const eventual = total("EVENTUAL");
  const avulsa = total("RECEITA");
  const deAtendimentos = somar(atends.map((a) => a.valor ?? 0));
  const operacionais = fixo + variavel; // eventual fica fora do custo por atendimento
  const custos = operacionais + eventual;
  const receita = deAtendimentos + avulsa;
  return {
    mes,
    receitaAtendimentos: deAtendimentos / 100,
    receitaAvulsa: avulsa / 100,
    receita: receita / 100,
    totalFixo: fixo / 100,
    totalVariavel: variavel / 100,
    totalEventual: eventual / 100,
    custos: custos / 100,
    lucro: (receita - custos) / 100,
    atendimentos: atends.length,
    custoMedioPorAtendimento: atends.length ? Math.round(operacionais / atends.length) / 100 : null,
  };
}
