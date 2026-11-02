package br.com.marcoslab.petshop.campanha;

import br.com.marcoslab.petshop.loja.Loja;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "campanha")
public class Campanha {

    public enum Status { ENVIANDO, CONCLUIDA }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "loja_id")
    private Loja loja;

    @Column(nullable = false, length = 80)
    private String titulo;

    @Column(nullable = false, length = 600)
    private String texto;

    /** Nome do modelo de MARKETING aprovado na Meta. */
    @Column(nullable = false, length = 80)
    private String template;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.ENVIANDO;

    @Column(name = "criada_em", nullable = false)
    private Instant criadaEm;

    protected Campanha() {
    }

    Campanha(Loja loja, String titulo, String texto, String template, Instant criadaEm) {
        this.loja = loja;
        this.titulo = titulo;
        this.texto = texto;
        this.template = template;
        this.criadaEm = criadaEm;
    }

    void concluir() {
        this.status = Status.CONCLUIDA;
    }

    public Long getId() { return id; }
    public Loja getLoja() { return loja; }
    public String getTitulo() { return titulo; }
    public String getTexto() { return texto; }
    public String getTemplate() { return template; }
    public Status getStatus() { return status; }
    public Instant getCriadaEm() { return criadaEm; }
}
