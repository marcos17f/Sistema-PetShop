package br.com.marcoslab.petshop.financeiro;

import br.com.marcoslab.petshop.agenda.TipoServico;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Formatos JSON do financeiro (espelham site/src/lib/tipos.ts). */
public final class FinanceiroDtos {

    private FinanceiroDtos() {
    }

    public record NovaCategoria(
            @NotBlank(message = "Informe o nome") @Size(max = 60) String nome,
            @NotNull(message = "Escolha o tipo") TipoLancamento tipo) {
    }

    public record CategoriaDto(Long id, String nome, TipoLancamento tipo) {
        static CategoriaDto de(Categoria c) {
            return new CategoriaDto(c.getId(), c.getNome(), c.getTipo());
        }
    }

    /** O tipo (fixo, variável, receita) vem da categoria escolhida. */
    public record DadosLancamento(
            @NotNull(message = "Informe a data") LocalDate data,
            @NotNull(message = "Escolha a categoria") Long categoriaId,
            @Size(max = 200) String descricao,
            @NotNull(message = "Informe o valor")
            @DecimalMin(value = "0.01", message = "O valor precisa ser maior que zero")
            @Digits(integer = 10, fraction = 2, message = "Use no máximo 2 casas decimais") BigDecimal valor,
            boolean recorrente) {

        /** Barra datas absurdas (ex.: ano 1900), que gerariam milhares de cópias recorrentes. */
        @AssertTrue(message = "Use uma data entre 2000 e 2100")
        public boolean isDataValida() {
            return data == null || (data.getYear() >= 2000 && data.getYear() <= 2100);
        }
    }

    public record LancamentoDto(Long id, LocalDate data, TipoLancamento tipo, Long categoriaId, String categoriaNome,
                                String descricao, BigDecimal valor, boolean recorrente) {
        static LancamentoDto de(Lancamento l) {
            return new LancamentoDto(l.getId(), l.getData(), l.getTipo(), l.getCategoria().getId(), l.getCategoria().getNome(),
                    l.getDescricao() == null ? "" : l.getDescricao(), l.getValor(), l.isRecorrente());
        }
    }

    /**
     * Números de um mês. custos = fixos + variáveis + eventuais (entra no lucro).
     * custoMedioPorAtendimento = (fixos + variáveis) ÷ atendimentos concluídos; null quando não houve atendimento.
     */
    public record ResumoMes(String mes, BigDecimal receitaAtendimentos, BigDecimal receitaAvulsa, BigDecimal receita,
                            BigDecimal totalFixo, BigDecimal totalVariavel, BigDecimal totalEventual, BigDecimal custos,
                            BigDecimal lucro, int atendimentos, BigDecimal custoMedioPorAtendimento) {
    }

    /** Mês escolhido, mês anterior (para comparar) e os últimos 6 meses (para o gráfico, do mais antigo ao atual). */
    public record Resumo(ResumoMes atual, ResumoMes anterior, List<ResumoMes> ultimosMeses) {
    }

    public record PrecoDto(TipoServico tipo, BigDecimal valor) {
    }

    public record NovoPreco(
            @NotNull(message = "Informe o valor")
            @DecimalMin(value = "0.00", message = "Valor inválido")
            @Digits(integer = 8, fraction = 2, message = "Use no máximo 2 casas decimais") BigDecimal valor) {
    }
}
