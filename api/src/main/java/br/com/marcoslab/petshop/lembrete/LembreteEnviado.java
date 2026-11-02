package br.com.marcoslab.petshop.lembrete;

import br.com.marcoslab.petshop.loja.Loja;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "lembrete_enviado")
public class LembreteEnviado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "loja_id")
    private Loja loja;

    @Column(nullable = false, length = 80)
    private String chave;

    @Column(name = "enviado_em", nullable = false)
    private Instant enviadoEm;

    protected LembreteEnviado() {
    }

    public LembreteEnviado(Loja loja, String chave, Instant enviadoEm) {
        this.loja = loja;
        this.chave = chave;
        this.enviadoEm = enviadoEm;
    }

    public String getChave() { return chave; }
}
