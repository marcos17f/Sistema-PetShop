package br.com.marcoslab.petshop.cliente;

import br.com.marcoslab.petshop.loja.Loja;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "tutor")
public class Tutor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "loja_id")
    private Loja loja;

    @Column(nullable = false, length = 120)
    private String nome;

    /** Só dígitos, com DDD (ex.: 89999998888). */
    @Column(nullable = false, length = 20)
    private String whatsapp;

    private String endereco;
    private String cpf;
    private String origem;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    /** Quando o cliente aceitou receber promoções (LGPD). null = não aceitou: nunca recebe campanha. */
    @Column(name = "consentimento_marketing_em")
    private Instant consentimentoMarketingEm;

    /** Link pessoal de "não quero mais receber" (aleatório, impossível de adivinhar). */
    @Column(name = "token_descadastro")
    private String tokenDescadastro;

    @Column(name = "descadastrado_em")
    private Instant descadastradoEm;

    /** Preenchido quando o cliente é excluído mas tinha atendimento concluído (dados pessoais apagados). */
    @Column(name = "removido_em")
    private Instant removidoEm;

    @OneToMany(mappedBy = "tutor")
    @OrderBy("nome")
    private List<Pet> pets = new ArrayList<>();

    protected Tutor() {
    }

    public Tutor(Loja loja, String nome, String whatsapp, String endereco, String cpf, String origem, Instant criadoEm) {
        this.loja = loja;
        this.nome = nome.trim();
        this.whatsapp = whatsapp.replaceAll("\\D", "");
        this.endereco = endereco;
        this.cpf = cpf;
        this.origem = origem;
        this.criadoEm = criadoEm;
    }

    public void alterar(String nome, String whatsapp, String endereco, String cpf, String origem) {
        this.nome = nome.trim();
        this.whatsapp = whatsapp.replaceAll("\\D", "");
        this.endereco = endereco;
        this.cpf = cpf;
        this.origem = origem;
    }

    /** LGPD: apaga os dados pessoais e mantém o registro, para os valores já recebidos continuarem no financeiro. */
    public void anonimizar(Instant agora) {
        this.consentimentoMarketingEm = null;
        this.tokenDescadastro = null;
        this.nome = "Cliente removido";
        this.whatsapp = "";
        this.endereco = null;
        this.cpf = null;
        this.origem = null;
        this.removidoEm = agora;
    }

    /** Registra o aceite de promoções (mantém a primeira data) e cria o link pessoal de descadastro. */
    public void aceitarPromocoes(Instant quando) {
        if (consentimentoMarketingEm == null) this.consentimentoMarketingEm = quando;
        this.descadastradoEm = null;
        if (tokenDescadastro == null) {
            byte[] b = new byte[16];
            ALEATORIO.nextBytes(b);
            this.tokenDescadastro = java.util.HexFormat.of().formatHex(b);
        }
    }

    /** Sai da lista de promoções; a data fica registrada como prova (LGPD). */
    public void sairDasPromocoes(Instant quando) {
        if (consentimentoMarketingEm != null) this.descadastradoEm = quando;
        this.consentimentoMarketingEm = null;
    }

    private static final java.security.SecureRandom ALEATORIO = new java.security.SecureRandom();

    public Instant getConsentimentoMarketingEm() { return consentimentoMarketingEm; }
    public Instant getDescadastradoEm() { return descadastradoEm; }
    public String getTokenDescadastro() { return tokenDescadastro; }

    public Long getId() { return id; }
    public Loja getLoja() { return loja; }
    public String getNome() { return nome; }
    public String getWhatsapp() { return whatsapp; }
    public String getEndereco() { return endereco; }
    public String getCpf() { return cpf; }
    public String getOrigem() { return origem; }
    public List<Pet> getPets() { return pets; }
}
