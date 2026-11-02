/**
 * Dados da loja. VALORES DE EXEMPLO (fictícios): trocar pelos reais do Cleiton antes de publicar.
 * O WhatsApp de exemplo não existe; os botões abrem o WhatsApp, mas a mensagem não chega a ninguém.
 */
export const loja = {
  slug: process.env.NEXT_PUBLIC_LOJA_SLUG ?? "petshop-cleiton",
  nome: "Pet Shop do Cleiton",
  cidade: "Goiânia – GO",
  endereco: "Rua Exemplo, 123 · Centro",
  horario: "Segunda a sexta, 8h às 18h · Sábado, 8h às 12h",
  /** Só números, com 55 + DDD. */
  whatsapp: "5562999990000",
  instagram: "petshopdocleiton",
  /** Minutos mínimos entre um atendimento e outro; manter igual a AGENDA_INTERVALO_MINUTOS da API. */
  intervaloAgendaMinutos: 40,
  servicos: [
    { nome: "Banho", texto: "Banho com produtos adequados a cada tipo de pelo." },
    { nome: "Tosa", texto: "Tosa higiênica e tosa completa." },
    { nome: "Vacinas", texto: "Carteira de vacinas em dia, com lembrete da próxima dose." },
    { nome: "Consultas", texto: "Consulta com veterinário parceiro, com hora marcada." },
  ],
};

/** Link do WhatsApp com a mensagem pronta (envio por clique, sem custo). */
export function linkWhatsapp(numero: string, mensagem: string) {
  const n = numero.replace(/\D/g, "");
  const comPais = n.startsWith("55") ? n : `55${n}`;
  return `https://wa.me/${comPais}?text=${encodeURIComponent(mensagem)}`;
}
