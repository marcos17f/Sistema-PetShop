package br.com.marcoslab.petshop.cliente;

import jakarta.validation.constraints.*;

import java.time.LocalDate;
import java.util.List;

/** Formatos JSON trocados com o painel (espelham site/src/lib/tipos.ts). */
public final class ClienteDtos {

    private ClienteDtos() {
    }

    public record NovoTutor(
            @NotBlank(message = "Informe o nome") @Size(max = 120) String nome,
            @NotBlank(message = "Informe o WhatsApp") @Pattern(regexp = "[\\d\\s()+-]{10,20}", message = "WhatsApp inválido") String whatsapp,
            @Size(max = 200) String endereco,
            @Size(max = 14) String cpf,
            @Size(max = 40) String origem) {
    }

    public record NovoPet(
            @NotBlank(message = "Informe o nome do pet") @Size(max = 80) String nome,
            @NotNull @Pattern(regexp = "Cão|Gato|Outro", message = "Espécie inválida") String especie,
            @Size(max = 80) String raca,
            @Positive(message = "Peso inválido") Double pesoKg,
            @Size(max = 500) String observacoes,
            @Min(value = 1, message = "O banho deve ser a cada 1 a 365 dias")
            @Max(value = 365, message = "O banho deve ser a cada 1 a 365 dias") Integer intervaloBanhoDias,
            /** Opcional: para cadastrar um cliente antigo já com o último banho certo, ou corrigir. */
            @PastOrPresent(message = "O último banho não pode ser no futuro") LocalDate ultimoBanho) {
    }

    public record PetDto(Long id, Long tutorId, String nome, String especie, String raca, Double pesoKg,
                         String observacoes, Integer intervaloBanhoDias, LocalDate ultimoBanho) {
        public static PetDto de(Pet p) {
            return new PetDto(p.getId(), p.getTutor().getId(), p.getNome(), p.getEspecie(), texto(p.getRaca()),
                    p.getPesoKg(), texto(p.getObservacoes()), p.getIntervaloBanhoDias(), p.getUltimoBanho());
        }
    }

    /** promocoesDesde: quando aceitou promoções (null = não aceitou); saiuDasPromocoesEm: quando pediu para sair. */
    public record TutorDto(Long id, String nome, String whatsapp, String endereco, String cpf, String origem, List<PetDto> pets,
                           java.time.Instant promocoesDesde, java.time.Instant saiuDasPromocoesEm) {
        public static TutorDto de(Tutor t) {
            return new TutorDto(t.getId(), t.getNome(), t.getWhatsapp(), texto(t.getEndereco()), texto(t.getCpf()),
                    texto(t.getOrigem()), t.getPets().stream().map(PetDto::de).toList(), t.getConsentimentoMarketingEm(),
                    t.getDescadastradoEm());
        }
    }

    public record Promocoes(boolean aceita) {
    }

    /** O painel trata texto vazio como "não informado"; nunca devolvemos null em campos de texto. */
    static String texto(String s) {
        return s == null ? "" : s;
    }

    public static String vazioParaNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
