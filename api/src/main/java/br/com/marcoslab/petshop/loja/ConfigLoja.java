package br.com.marcoslab.petshop.loja;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.*;

import java.time.LocalTime;

/** Configurações editáveis no painel. Também é o formato devolvido (texto vazio em vez de null). */
public record ConfigLoja(
        @NotBlank(message = "Informe o nome da loja") @Size(max = 120) String nome,
        @NotBlank(message = "Informe o WhatsApp da loja")
        @Pattern(regexp = "[\\d\\s()+-]{10,20}", message = "WhatsApp inválido") String whatsapp,
        @Size(max = 80) String cidade,
        @Size(max = 200) String endereco,
        @Size(max = 120) String horarioTexto,
        @Pattern(regexp = "[A-Za-z0-9._]{0,30}", message = "Use só o usuário do Instagram, sem @") String instagram,
        @Size(max = 300) @Pattern(regexp = "|https://.+", message = "O link precisa começar com https://") String linkAvaliacao,
        @NotNull @Min(value = 10, message = "Use de 10 a 240 minutos") @Max(value = 240, message = "Use de 10 a 240 minutos")
        Integer intervaloAgendaMinutos,
        @NotNull(message = "Informe quando abre") @JsonFormat(pattern = "HH:mm") LocalTime abre,
        @NotNull(message = "Informe quando fecha") @JsonFormat(pattern = "HH:mm") LocalTime fecha,
        @JsonFormat(pattern = "HH:mm") LocalTime sabadoAbre,
        @JsonFormat(pattern = "HH:mm") LocalTime sabadoFecha) {

    @AssertTrue(message = "O horário de fechar precisa ser depois do de abrir")
    public boolean isHorarioValido() {
        boolean semana = abre == null || fecha == null || fecha.isAfter(abre);
        boolean sabado = (sabadoAbre == null) == (sabadoFecha == null)
                && (sabadoAbre == null || sabadoFecha.isAfter(sabadoAbre));
        return semana && sabado;
    }

    static ConfigLoja de(Loja l) {
        return new ConfigLoja(l.getNome(), texto(l.getWhatsapp()), texto(l.getCidade()), texto(l.getEndereco()),
                texto(l.getHorarioTexto()), texto(l.getInstagram()), texto(l.getLinkAvaliacao()), l.getIntervaloAgendaMinutos(),
                l.getAbre(), l.getFecha(), l.getSabadoAbre(), l.getSabadoFecha());
    }

    private static String texto(String s) {
        return s == null ? "" : s;
    }
}
