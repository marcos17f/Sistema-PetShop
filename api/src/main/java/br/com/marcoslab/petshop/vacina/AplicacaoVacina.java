package br.com.marcoslab.petshop.vacina;

import br.com.marcoslab.petshop.cliente.Pet;
import jakarta.persistence.*;

import java.time.LocalDate;

@Entity
@Table(name = "aplicacao_vacina")
public class AplicacaoVacina {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pet_id")
    private Pet pet;

    @Column(nullable = false, length = 80)
    private String vacina;

    private String dose;

    @Column(name = "aplicada_em", nullable = false)
    private LocalDate aplicadaEm;

    /** Data do reforço; é ela que gera o lembrete. */
    @Column(name = "proxima_em")
    private LocalDate proximaEm;

    protected AplicacaoVacina() {
    }

    public AplicacaoVacina(Pet pet, String vacina, String dose, LocalDate aplicadaEm, LocalDate proximaEm) {
        this.pet = pet;
        this.vacina = vacina.trim();
        this.dose = dose;
        this.aplicadaEm = aplicadaEm;
        this.proximaEm = proximaEm;
    }

    public Long getId() { return id; }
    public Pet getPet() { return pet; }
    public String getVacina() { return vacina; }
    public String getDose() { return dose; }
    public LocalDate getAplicadaEm() { return aplicadaEm; }
    public LocalDate getProximaEm() { return proximaEm; }
}
