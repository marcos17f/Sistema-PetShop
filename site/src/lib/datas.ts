/** Datas sempre como texto yyyy-MM-dd, no fuso de Goiânia. */

export function hoje(): string {
  return new Date().toLocaleDateString("en-CA", { timeZone: "America/Sao_Paulo" });
}

export function somarDias(iso: string, dias: number): string {
  const d = new Date(`${iso}T12:00:00`);
  d.setDate(d.getDate() + dias);
  return d.toISOString().slice(0, 10);
}

export function diasEntre(de: string, ate: string): number {
  return Math.round((new Date(`${ate}T12:00:00`).getTime() - new Date(`${de}T12:00:00`).getTime()) / 86_400_000);
}

export function formatar(iso: string | null, comAno = false): string {
  if (!iso) return "—";
  const [a, m, d] = iso.split("-");
  return comAno ? `${d}/${m}/${a}` : `${d}/${m}`;
}

export function diaPorExtenso(iso: string): string {
  const txt = new Date(`${iso}T12:00:00`).toLocaleDateString("pt-BR", { weekday: "long", day: "2-digit", month: "2-digit" });
  return txt.charAt(0).toUpperCase() + txt.slice(1);
}

export type Situacao = { texto: "Em dia" | "Vence em breve" | "Atrasada" | "Dose única"; classe: string };

/** Situação de uma vacina pela data da próxima dose (7 dias = "vence em breve"). */
export function situacaoVacina(proximaEm: string | null, referencia = hoje()): Situacao {
  if (!proximaEm) return { texto: "Dose única", classe: "bg-[#e9eceb] text-[#3f4b48]" };
  const faltam = diasEntre(referencia, proximaEm);
  if (faltam < 0) return { texto: "Atrasada", classe: "bg-[#fbdad3] text-[#9b2c12]" };
  if (faltam <= 7) return { texto: "Vence em breve", classe: "bg-[#fdecc8] text-[#7a4a00]" };
  return { texto: "Em dia", classe: "bg-[#ddf2ea] text-[#0b5e4f]" };
}

export function proximoBanho(ultimo: string | null, intervalo: number | null): string | null {
  return ultimo && intervalo ? somarDias(ultimo, intervalo) : null;
}

/* ---------- meses (yyyy-MM) ---------- */

export function mesAtual(): string {
  return hoje().slice(0, 7);
}

export function somarMeses(mes: string, n: number): string {
  const [a, m] = mes.split("-").map(Number);
  const d = new Date(Date.UTC(a, m - 1 + n, 1));
  return d.toISOString().slice(0, 7);
}

/** Mesmo dia no mês seguinte; 31/01 vira 28/02 (igual ao LocalDate.plusMonths da API). */
export function mesmoDiaNoProximoMes(iso: string): string {
  const prox = somarMeses(iso.slice(0, 7), 1);
  const [a, m] = prox.split("-").map(Number);
  const ultimo = new Date(Date.UTC(a, m, 0)).getUTCDate();
  return `${prox}-${String(Math.min(Number(iso.slice(8, 10)), ultimo)).padStart(2, "0")}`;
}

export function nomeMes(mes: string, curto = false): string {
  const txt = new Date(`${mes}-15T12:00:00`).toLocaleDateString("pt-BR", curto ? { month: "short" } : { month: "long", year: "numeric" });
  return (txt.charAt(0).toUpperCase() + txt.slice(1)).replace(".", "");
}
