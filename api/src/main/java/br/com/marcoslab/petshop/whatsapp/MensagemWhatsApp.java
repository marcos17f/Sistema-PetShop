package br.com.marcoslab.petshop.whatsapp;

import br.com.marcoslab.petshop.loja.Loja;
import jakarta.persistence.*;

import java.time.Instant;

/** Toda mensagem que o sistema manda (ou simula, sem a API oficial). Apagada depois de 90 dias (LGPD). */
@Entity
@Table(name = "mensagem_whatsapp")
public class MensagemWhatsApp {

    public enum Tipo { LEMBRETE, CONFIRMACAO, RESPOSTA_RAPIDA, CAMPANHA, AVALIACAO, TESTE }

    /** SIMULADA = sem API oficial configurada: nada saiu, a loja pode mandar por clique. */
    public enum Status { SIMULADA, ENVIADA, FALHOU }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "loja_id")
    private Loja loja;

    @Column(nullable = false, length = 20)
    private String para;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Tipo tipo;

    @Column(nullable = false, length = 1000)
    private String texto;

    private String template;
    private String chave;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    private String erro;

    @Column(name = "id_externo")
    private String idExterno;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    protected MensagemWhatsApp() {
    }

    MensagemWhatsApp(Loja loja, String para, Tipo tipo, String texto, String template, String chave, Instant criadoEm) {
        this.loja = loja;
        this.para = para;
        this.tipo = tipo;
        this.texto = texto.length() > 1000 ? texto.substring(0, 1000) : texto;
        this.template = template;
        this.chave = chave;
        this.criadoEm = criadoEm;
        this.status = Status.SIMULADA;
    }

    void enviada(String idExterno) {
        this.status = Status.ENVIADA;
        this.idExterno = idExterno;
    }

    void falhou(String erro) {
        this.status = Status.FALHOU;
        this.erro = erro.length() > 300 ? erro.substring(0, 300) : erro;
    }

    public Long getId() { return id; }
    public String getPara() { return para; }
    public Tipo getTipo() { return tipo; }
    public String getTexto() { return texto; }
    public Status getStatus() { return status; }
    public String getErro() { return erro; }
    public Instant getCriadoEm() { return criadoEm; }
}
