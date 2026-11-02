/** Tipos compartilhados entre o painel e a API (espelham os DTOs do Spring Boot). */

export type Pet = {
  id: number;
  tutorId: number;
  nome: string;
  especie: "Cão" | "Gato" | "Outro";
  raca: string;
  pesoKg: number | null;
  observacoes: string;
  /** Intervalo de retorno do banho em dias; null = sem retorno automático. */
  intervaloBanhoDias: number | null;
  ultimoBanho: string | null; // yyyy-MM-dd
};

export type Tutor = {
  id: number;
  nome: string;
  whatsapp: string;
  endereco: string;
  cpf: string;
  origem: string;
  pets: Pet[];
};

export type AplicacaoVacina = {
  id: number;
  petId: number;
  vacina: string;
  dose: string;
  aplicadaEm: string; // yyyy-MM-dd
  proximaEm: string | null;
};

export type TipoServico = "BANHO" | "TOSA" | "BANHO_E_TOSA" | "VACINA" | "CONSULTA";
export type StatusAgendamento = "AGUARDANDO" | "CONFIRMADO" | "EM_ATENDIMENTO" | "CONCLUIDO" | "CANCELADO";

export type Agendamento = {
  id: number;
  petId: number;
  petNome: string;
  tutorNome: string;
  data: string; // yyyy-MM-dd
  hora: string; // HH:mm
  tipo: TipoServico;
  descricao: string;
  status: StatusAgendamento;
  /** Valor cobrado (receita quando concluído); null = ainda não definido. */
  valor: number | null;
};

export type TipoLembrete = "VACINA" | "BANHO" | "AGENDAMENTO";

export type Lembrete = {
  /** Identifica o lembrete (tipo:id:data) para marcar como enviado. */
  chave: string;
  tipo: TipoLembrete;
  petId: number;
  petNome: string;
  tutorNome: string;
  whatsapp: string;
  data: string;
  mensagem: string;
  enviado: boolean;
};

export const NOMES_SERVICO: Record<TipoServico, string> = {
  BANHO: "Banho",
  TOSA: "Tosa",
  BANHO_E_TOSA: "Banho e tosa",
  VACINA: "Vacina",
  CONSULTA: "Consulta",
};

export const NOMES_STATUS: Record<StatusAgendamento, string> = {
  AGUARDANDO: "Aguardando",
  CONFIRMADO: "Confirmado",
  EM_ATENDIMENTO: "Em atendimento",
  CONCLUIDO: "Concluído",
  CANCELADO: "Cancelado",
};

/* ---------- financeiro ---------- */

/** EVENTUAL = despesa fora do dia a dia (equipamento, conserto): entra no lucro, não no custo por atendimento. */
export type TipoLancamento = "FIXO" | "VARIAVEL" | "EVENTUAL" | "RECEITA";

export type Categoria = { id: number; nome: string; tipo: TipoLancamento };

export type Lancamento = {
  id: number;
  data: string; // yyyy-MM-dd
  tipo: TipoLancamento;
  categoriaId: number;
  categoriaNome: string;
  descricao: string;
  valor: number;
  /** Só custo fixo: o sistema lança sozinho no mês seguinte. */
  recorrente: boolean;
};

export type ResumoMes = {
  mes: string; // yyyy-MM
  receitaAtendimentos: number;
  receitaAvulsa: number;
  receita: number;
  totalFixo: number;
  totalVariavel: number;
  totalEventual: number;
  /** fixos + variáveis + eventuais */
  custos: number;
  lucro: number;
  atendimentos: number;
  /** (fixos + variáveis) ÷ atendimentos; null quando não houve atendimento concluído no mês. */
  custoMedioPorAtendimento: number | null;
};

export type Resumo = { atual: ResumoMes; anterior: ResumoMes; ultimosMeses: ResumoMes[] };

export type PrecoServico = { tipo: TipoServico; valor: number | null };

/** Despesa fixa ou variável pode repetir todo mês (mesma regra da API). */
export const podeRepetir = (tipo: TipoLancamento) => tipo === "FIXO" || tipo === "VARIAVEL";

export const NOMES_LANCAMENTO: Record<TipoLancamento, string> = {
  FIXO: "Custos fixos",
  VARIAVEL: "Custos variáveis",
  EVENTUAL: "Despesas eventuais",
  RECEITA: "Receitas",
};

const brl = new Intl.NumberFormat("pt-BR", { style: "currency", currency: "BRL" });

export function reais(v: number | null): string {
  return v === null ? "—" : brl.format(v);
}

/** "1.234,50", "12,5" ou "12.50" → número; null se não for um valor válido com até 2 casas. */
export function lerValor(txt: string): number | null {
  const t = txt.trim();
  const limpo = t.includes(",") ? t.replace(/\./g, "").replace(",", ".") : t;
  return /^\d+(\.\d{1,2})?$/.test(limpo) ? Number(limpo) : null;
}

/** Registro do histórico de alterações (só leitura; ninguém edita nem apaga). */
export type RegistroHistorico = {
  em: string; // ISO
  usuario: string;
  acao: "CRIOU" | "ALTEROU" | "EXCLUIU" | "COPIOU";
  entidade: string;
  entidadeId: string;
  antes: string | null;
  depois: string | null;
};

/** Configurações da loja (tela Configurações); o site público lê as mesmas. Horas em HH:mm; sábado null = fechado. */
export type ConfigLoja = {
  nome: string;
  whatsapp: string;
  cidade: string;
  endereco: string;
  horarioTexto: string;
  instagram: string;
  linkAvaliacao: string;
  intervaloAgendaMinutos: number;
  abre: string;
  fecha: string;
  sabadoAbre: string | null;
  sabadoFecha: string | null;
};
