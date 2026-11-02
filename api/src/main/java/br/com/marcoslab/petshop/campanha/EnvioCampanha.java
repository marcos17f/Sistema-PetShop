package br.com.marcoslab.petshop.campanha;

import br.com.marcoslab.petshop.cliente.Tutor;
import jakarta.persistence.*;

import java.time.Instant;

/** Um destinatário da campanha. PULADO = saiu da lista antes de chegar a vez dele. */
@Entity
@Table(name = "campanha_envio")
public class EnvioCampanha {

    public enum Status { PENDENTE, ENVIADA, SIMULADA, FALHOU, PULADO }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "campanha_id")
    private Campanha campanha;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tutor_id")
    private Tutor tutor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.PENDENTE;

    @Column(name = "enviado_em")
    private Instant enviadoEm;

    protected EnvioCampanha() {
    }

    EnvioCampanha(Campanha campanha, Tutor tutor) {
        this.campanha = campanha;
        this.tutor = tutor;
    }

    void marcar(Status status, Instant quando) {
        this.status = status;
        this.enviadoEm = quando;
    }

    public Tutor getTutor() { return tutor; }
    public Status getStatus() { return status; }
}
