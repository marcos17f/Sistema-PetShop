package br.com.marcoslab.petshop.lembrete;

import java.time.LocalDate;

/** Lembrete calculado para uma data (não fica salvo; só o "enviado" fica). Espelha o tipo Lembrete do painel. */
public record Lembrete(String chave, Tipo tipo, Long petId, String petNome, String tutorNome, String whatsapp,
                       LocalDate data, String mensagem, boolean enviado) {

    public enum Tipo { VACINA, BANHO, AGENDAMENTO }

    Lembrete comEnviado(boolean valor) {
        return new Lembrete(chave, tipo, petId, petNome, tutorNome, whatsapp, data, mensagem, valor);
    }
}
