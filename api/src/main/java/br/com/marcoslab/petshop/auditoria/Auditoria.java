package br.com.marcoslab.petshop.auditoria;

import br.com.marcoslab.petshop.loja.Loja;
import jakarta.persistence.*;
import org.hibernate.annotations.Immutable;

import java.time.Instant;

/** Um registro do histórico de alterações. Imutável: o Hibernate nunca gera UPDATE para ele. */
@Entity
@Immutable
@Table(name = "auditoria")
public class Auditoria {

    public enum Acao { CRIOU, ALTEROU, EXCLUIU, COPIOU }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "loja_id")
    private Loja loja;

    @Column(nullable = false, length = 60)
    private String usuario;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Acao acao;

    @Column(nullable = false, length = 40)
    private String entidade;

    @Column(name = "entidade_id", nullable = false, length = 40)
    private String entidadeId;

    private String antes;

    private String depois;

    @Column(nullable = false)
    private Instant em;

    protected Auditoria() {
    }

    Auditoria(Loja loja, String usuario, Acao acao, String entidade, String entidadeId, String antes, String depois, Instant em) {
        this.loja = loja;
        this.usuario = usuario;
        this.acao = acao;
        this.entidade = entidade;
        this.entidadeId = entidadeId;
        this.antes = antes;
        this.depois = depois;
        this.em = em;
    }

    public Instant getEm() { return em; }
    public String getUsuario() { return usuario; }
    public Acao getAcao() { return acao; }
    public String getEntidade() { return entidade; }
    public String getEntidadeId() { return entidadeId; }
    public String getAntes() { return antes; }
    public String getDepois() { return depois; }
}
