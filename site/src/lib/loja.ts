import type { ConfigLoja } from "./tipos";

/**
 * Nome, contato, horários e links agora são editados no painel (tela Configurações) e lidos da API.
 * Os valores abaixo são de EXEMPLO (fictícios) e só valem no modo demonstração ou se a API estiver fora do ar.
 * O WhatsApp de exemplo não existe: os botões abrem o WhatsApp, mas a mensagem não chega a ninguém.
 */
export const configExemplo: ConfigLoja = {
  nome: "A Arca Pet",
  whatsapp: "62999990000",
  cidade: "Goiânia – GO",
  endereco: "Rua Exemplo, 123 · Centro",
  horarioTexto: "Segunda a sexta, 8h às 18h · Sábado, 8h às 12h",
  instagram: "petshopdocleiton",
  linkAvaliacao: "",
  intervaloAgendaMinutos: 40,
  abre: "08:00",
  fecha: "18:00",
  sabadoAbre: "08:00",
  sabadoFecha: "12:00",
};

export const loja = {
  slug: process.env.NEXT_PUBLIC_LOJA_SLUG ?? "petshop-cleiton",
  nome: configExemplo.nome,
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
