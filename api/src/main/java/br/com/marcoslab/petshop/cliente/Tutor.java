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

    public Long getId() { return id; }
    public Loja getLoja() { return loja; }
    public String getNome() { return nome; }
    public String getWhatsapp() { return whatsapp; }
    public String getEndereco() { return endereco; }
    public String getCpf() { return cpf; }
    public String getOrigem() { return origem; }
    public List<Pet> getPets() { return pets; }
}
