import type { ConfigLoja } from "./tipos";

/**
 * Nome, contato, horários e links são editados no painel (tela Configurações) e lidos da API.
 * Os valores abaixo são a reserva para o modo demonstração ou se a API estiver fora do ar.
 */
export const configExemplo: ConfigLoja = {
  nome: "A Arca Pet",
  whatsapp: "", // [PREENCHER] WhatsApp da loja no formato 55DDDNUMERO
  cidade: "Aparecida de Goiânia – GO",
  endereco: "Rua 8, Quadra 055, Lote 36, Sala 2 – Jardim Tiradentes",
  horarioTexto: "[PREENCHER] horário de funcionamento",
  instagram: "aarcapet.oficial",
  linkAvaliacao: "",
  intervaloAgendaMinutos: 40,
  // [PREENCHER] horário real: estes valores definem os horários livres do agendamento online
  abre: "08:00",
  fecha: "18:00",
  sabadoAbre: "08:00",
  sabadoFecha: "12:00",
};

export const loja = {
  slug: process.env.NEXT_PUBLIC_LOJA_SLUG ?? "petshop-cleiton",
  nome: configExemplo.nome,
  nomeAlternativo: "A Arca Petshop",
  slogan: "Buscando sempre o melhor para o seu pet!",
  responsavel: {
    nome: "Cleiton Rocha Primo",
    apelido: "Cleiton Tosador",
    instagram: "cleitonrochatosador",
    facebook: "", // [PREENCHER] link da página "Cleiton Tosador" no Facebook
  },
  areaAtendida: ["Aparecida de Goiânia", "Goiânia"],
  servicos: [
    { nome: "Banho", texto: "Banho com produtos adequados a cada tipo de pelo." },
    { nome: "Tosa", texto: "Tosa higiênica, na máquina ou na tesoura." },
    { nome: "Banho & tosa", texto: "O pacote completo: banho e tosa no mesmo horário." },
    { nome: "Hidratação", texto: "Pelo macio, brilhante e bem cuidado." },
    { nome: "Unhas e ouvidos", texto: "Corte de unhas e limpeza de ouvidos com calma e carinho." },
    { nome: "Acessórios", texto: "Lacinho ou gravatinha para sair ainda mais bonito." },
    // TODO: confirmar com o cliente (vacina e consulta existem na agenda do sistema, mas não na lista de serviços)
    { nome: "Vacinas", texto: "Carteira de vacinas em dia, com lembrete da próxima dose." },
    // TODO: confirmar com o cliente
    { nome: "Consultas", texto: "Consulta com veterinário parceiro, com hora marcada." },
  ],
};

/** Link do WhatsApp com a mensagem pronta (envio por clique, sem custo). */
export function linkWhatsapp(numero: string, mensagem: string) {
  const n = numero.replace(/\D/g, "");
  const comPais = n.startsWith("55") ? n : `55${n}`;
  return `https://wa.me/${comPais}?text=${encodeURIComponent(mensagem)}`;
}
