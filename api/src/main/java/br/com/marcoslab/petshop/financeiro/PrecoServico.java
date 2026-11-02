package br.com.marcoslab.petshop.financeiro;

import br.com.marcoslab.petshop.agenda.TipoServico;
import jakarta.persistence.*;

import java.io.Serializable;
import java.math.BigDecimal;

/** Preço padrão de cada serviço; preenche o valor do agendamento ao concluir. */
@Entity
@Table(name = "preco_servico")
@IdClass(PrecoServico.Chave.class)
public class PrecoServico {

    public record Chave(Long lojaId, TipoServico tipoServico) implements Serializable {
        public Chave() {
            this(null, null);
        }
    }

    @Id
    @Column(name = "loja_id")
    private Long lojaId;

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_servico", length = 20)
    private TipoServico tipoServico;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal valor;

    protected PrecoServico() {
    }

    public PrecoServico(Long lojaId, TipoServico tipoServico, BigDecimal valor) {
        this.lojaId = lojaId;
        this.tipoServico = tipoServico;
        this.valor = valor;
    }

    public void setValor(BigDecimal valor) { this.valor = valor; }
    public TipoServico getTipoServico() { return tipoServico; }
    public BigDecimal getValor() { return valor; }
}
