package br.com.marcoslab.petshop.usuario;

import br.com.marcoslab.petshop.loja.Loja;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "usuario")
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "loja_id")
    private Loja loja;

    @Column(nullable = false, length = 80)
    private String nome;

    @Column(nullable = false, unique = true, length = 40)
    private String login;

    /** Hash bcrypt com o prefixo {bcrypt}; a senha em si nunca é guardada. */
    @Column(name = "senha_hash", nullable = false, length = 100)
    private String senhaHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Papel papel;

    @Column(nullable = false)
    private boolean ativo = true;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    protected Usuario() {
    }

    Usuario(Loja loja, String nome, String login, String senhaHash, Papel papel, Instant criadoEm) {
        this.loja = loja;
        this.nome = nome;
        this.login = login;
        this.senhaHash = senhaHash;
        this.papel = papel;
        this.criadoEm = criadoEm;
    }

    void alterar(String nome, Papel papel, boolean ativo) {
        this.nome = nome;
        this.papel = papel;
        this.ativo = ativo;
    }

    void trocarSenha(String senhaHash) {
        this.senhaHash = senhaHash;
    }

    public Long getId() { return id; }
    public Loja getLoja() { return loja; }
    public String getNome() { return nome; }
    public String getLogin() { return login; }
    public String getSenhaHash() { return senhaHash; }
    public Papel getPapel() { return papel; }
    public boolean isAtivo() { return ativo; }
}
