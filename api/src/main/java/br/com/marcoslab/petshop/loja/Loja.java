package br.com.marcoslab.petshop.loja;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "loja")
public class Loja {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 80)
    private String slug;

    @Column(nullable = false, length = 120)
    private String nome;

    @Column(length = 20)
    private String whatsapp;

    protected Loja() {
    }

    public Long getId() {
        return id;
    }

    public String getSlug() {
        return slug;
    }

    public String getNome() {
        return nome;
    }

    public String getWhatsapp() {
        return whatsapp;
    }
}
