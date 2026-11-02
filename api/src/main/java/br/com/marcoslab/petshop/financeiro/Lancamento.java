package br.com.marcoslab.petshop.financeiro;

import br.com.marcoslab.petshop.loja.Loja;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Custo fixo, custo variável ou receita avulsa (venda de ração, produto). O tipo vem da categoria. */
@Entity
@Table(name = "lancamento_financeiro")
public class Lancamento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "loja_id")
    private Loja loja;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "categoria_id")
    private Categoria categoria;

    @Column(nullable = false)
    private LocalDate data;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoLancamento tipo;

    private String descricao;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal valor;

    @Column(nullable = false)
    private boolean recorrente;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "copiado_de_id")
    private Lancamento copiadoDe;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    protected Lancamento() {
    }

    public Lancamento(Loja loja, Categoria categoria, LocalDate data, String descricao, BigDecimal valor,
                      boolean recorrente, Instant criadoEm) {
        this.loja = loja;
        this.criadoEm = criadoEm;
        alterar(categoria, data, descricao, valor, recorrente);
    }

    /** Só despesa fixa ou variável pode ser recorrente. */
    public void alterar(Categoria categoria, LocalDate data, String descricao, BigDecimal valor, boolean recorrente) {
        this.categoria = categoria;
        this.tipo = categoria.getTipo();
        this.data = data;
        this.descricao = descricao;
        this.valor = valor;
        this.recorrente = recorrente && tipo.podeRepetir();
    }

    /** Cópia no mês seguinte, com o mesmo valor (o dono ajusta depois, ex.: conta de energia). */
    public Lancamento copiaParaProximoMes(Instant agora) {
        Lancamento c = new Lancamento(loja, categoria, data.plusMonths(1), descricao, valor, true, agora);
        c.copiadoDe = this;
        return c;
    }

    /** Excluir uma cópia encerra a recorrência: a anterior deixa de ser copiada. */
    public void pararRecorrencia() {
        this.recorrente = false;
    }

    public Long getId() { return id; }
    public Categoria getCategoria() { return categoria; }
    public LocalDate getData() { return data; }
    public TipoLancamento getTipo() { return tipo; }
    public String getDescricao() { return descricao; }
    public BigDecimal getValor() { return valor; }
    public boolean isRecorrente() { return recorrente; }
    public Lancamento getCopiadoDe() { return copiadoDe; }
}
