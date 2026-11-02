package br.com.marcoslab.petshop.agenda;

import br.com.marcoslab.petshop.cliente.Pet;
import br.com.marcoslab.petshop.loja.Loja;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(name = "agendamento")
public class Agendamento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "loja_id")
    private Loja loja;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pet_id")
    private Pet pet;

    @Column(nullable = false)
    private LocalDate data;

    @Column(nullable = false)
    private LocalTime hora;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoServico tipo;

    private String descricao;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusAgendamento status;

    /** Valor cobrado; conta como receita quando o atendimento está concluído. */
    @Column(precision = 10, scale = 2)
    private BigDecimal valor;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    protected Agendamento() {
    }

    public Agendamento(Loja loja, Pet pet, LocalDate data, LocalTime hora, TipoServico tipo, String descricao, Instant criadoEm) {
        this.loja = loja;
        this.pet = pet;
        this.data = data;
        this.hora = hora;
        this.tipo = tipo;
        this.descricao = descricao;
        this.status = StatusAgendamento.AGUARDANDO;
        this.criadoEm = criadoEm;
    }

    /**
     * Muda o status e, se informado, o valor cobrado.
     * Banho concluído atualiza o "último banho" do pet, que alimenta o lembrete de retorno.
     */
    public void mudarStatus(StatusAgendamento novo, BigDecimal valor) {
        this.status = novo;
        if (valor != null) {
            this.valor = valor;
        }
        if (novo == StatusAgendamento.CONCLUIDO && tipo.incluiBanho()) {
            pet.registrarBanho(data);
        }
    }

    public Long getId() { return id; }
    public Pet getPet() { return pet; }
    public LocalDate getData() { return data; }
    public LocalTime getHora() { return hora; }
    public TipoServico getTipo() { return tipo; }
    public String getDescricao() { return descricao; }
    public StatusAgendamento getStatus() { return status; }
    public BigDecimal getValor() { return valor; }
}
