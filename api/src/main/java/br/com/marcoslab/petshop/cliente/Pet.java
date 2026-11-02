package br.com.marcoslab.petshop.cliente;

import jakarta.persistence.*;

import java.time.LocalDate;

@Entity
@Table(name = "pet")
public class Pet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tutor_id")
    private Tutor tutor;

    @Column(nullable = false, length = 80)
    private String nome;

    /** "Cão", "Gato" ou "Outro" (mesmos valores do painel). */
    @Column(nullable = false, length = 20)
    private String especie;

    private String raca;

    @Column(name = "peso_kg")
    private Double pesoKg;

    private String observacoes;

    /** Dias até o próximo banho; null = sem lembrete de banho. */
    @Column(name = "intervalo_banho_dias")
    private Integer intervaloBanhoDias;

    @Column(name = "ultimo_banho")
    private LocalDate ultimoBanho;

    protected Pet() {
    }

    public Pet(Tutor tutor, String nome, String especie, String raca, Double pesoKg, String observacoes, Integer intervaloBanhoDias) {
        this.tutor = tutor;
        this.nome = nome.trim();
        this.especie = especie;
        this.raca = raca;
        this.pesoKg = pesoKg;
        this.observacoes = observacoes;
        this.intervaloBanhoDias = intervaloBanhoDias;
    }

    /** Data em que o próximo banho vence, ou null se o pet não tem retorno programado. */
    public LocalDate proximoBanho() {
        if (ultimoBanho == null || intervaloBanhoDias == null) return null;
        return ultimoBanho.plusDays(intervaloBanhoDias);
    }

    public void registrarBanho(LocalDate data) {
        if (ultimoBanho == null || data.isAfter(ultimoBanho)) {
            this.ultimoBanho = data;
        }
    }

    /** Corrige os dados do pet (inclusive o último banho, que define o próximo lembrete). */
    public void alterar(String nome, String especie, String raca, Double pesoKg, String observacoes,
                        Integer intervaloBanhoDias, LocalDate ultimoBanho) {
        this.nome = nome.trim();
        this.especie = especie;
        this.raca = raca;
        this.pesoKg = pesoKg;
        this.observacoes = observacoes;
        this.intervaloBanhoDias = intervaloBanhoDias;
        this.ultimoBanho = ultimoBanho;
    }

    public Long getId() { return id; }
    public Tutor getTutor() { return tutor; }
    public String getNome() { return nome; }
    public String getEspecie() { return especie; }
    public String getRaca() { return raca; }
    public Double getPesoKg() { return pesoKg; }
    public String getObservacoes() { return observacoes; }
    public Integer getIntervaloBanhoDias() { return intervaloBanhoDias; }
    public LocalDate getUltimoBanho() { return ultimoBanho; }
}
