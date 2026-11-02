package br.com.marcoslab.petshop.online;

import br.com.marcoslab.petshop.agenda.TipoServico;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.*;

import java.time.LocalDate;
import java.time.LocalTime;

/** O que o cliente preenche na página /agendar. */
public record PedidoOnline(
        @NotBlank(message = "Informe seu nome") @Size(min = 2, max = 120, message = "Informe seu nome") String nome,
        @NotBlank(message = "Informe seu WhatsApp")
        @Pattern(regexp = "[\\d\\s()+-]{10,20}", message = "WhatsApp inválido (use DDD + número)") String whatsapp,
        @NotBlank(message = "Informe o nome do pet") @Size(max = 80) String petNome,
        @NotNull @Pattern(regexp = "Cão|Gato|Outro", message = "Espécie inválida") String especie,
        @NotNull(message = "Escolha o serviço") TipoServico tipo,
        @NotNull(message = "Escolha o dia") LocalDate data,
        @NotNull(message = "Escolha o horário") @JsonFormat(pattern = "HH:mm") LocalTime hora,
        @Size(max = 200) String observacao,
        boolean buscaMoto,
        @Size(max = 200) String enderecoBusca,
        /** LGPD: aceite obrigatório do uso dos dados para o agendamento. */
        @AssertTrue(message = "É preciso aceitar o uso dos dados para agendar") boolean aceiteDados,
        /** LGPD: opcional, e só com ele o cliente recebe promoções. */
        boolean aceitePromocoes,
        @Size(max = 30) String origem,
        /** Campo invisível na página: pessoa não preenche; robô preenche. */
        String site) {

    @AssertTrue(message = "Informe o endereço para a busca de moto")
    public boolean isEnderecoDaBusca() {
        return !buscaMoto || (enderecoBusca != null && !enderecoBusca.isBlank());
    }
}
