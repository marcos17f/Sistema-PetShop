package br.com.marcoslab.petshop.financeiro;

import br.com.marcoslab.petshop.loja.Loja;
import jakarta.persistence.*;

@Entity
@Table(name = "categoria_financeira")
public class Categoria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "loja_id")
    private Loja loja;

    @Column(nullable = false, length = 60)
    private String nome;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoLancamento tipo;

    protected Categoria() {
    }

    public Categoria(Loja loja, String nome, TipoLancamento tipo) {
        this.loja = loja;
        this.nome = nome;
        this.tipo = tipo;
    }

    public Long getId() { return id; }
    public String getNome() { return nome; }
    public TipoLancamento getTipo() { return tipo; }
}
