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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Canal canal = Canal.PAINEL;

    /** A loja busca o pet de moto no endereço informado. */
    @Column(name = "busca_moto", nullable = false)
    private boolean buscaMoto;

    @Column(name = "endereco_busca")
    private String enderecoBusca;

    protected Agendamento() {
    }

    public Agendamento(Loja loja, Pet pet, LocalDate data, LocalTime hora, TipoServico tipo, String descricao,
                       boolean buscaMoto, String enderecoBusca, Canal canal, Instant criadoEm) {
        this.canal = canal;
        this.buscaMoto = buscaMoto;
        this.enderecoBusca = buscaMoto ? enderecoBusca : null;
        this.loja = loja;
        this.pet = pet;
        this.data = data;
        this.hora = hora;
        this.tipo = tipo;
        this.descricao = descricao;
        this.status = StatusAgendamento.AGUARDANDO;
        this.criadoEm = criadoEm;
    }

    /** Corrige pet, serviço, data, hora ou detalhe de um agendamento ainda não concluído. */
    public void remarcar(Pet pet, LocalDate data, LocalTime hora, TipoServico tipo, String descricao,
                         boolean buscaMoto, String enderecoBusca) {
        this.buscaMoto = buscaMoto;
        this.enderecoBusca = buscaMoto ? enderecoBusca : null;
        this.pet = pet;
        this.data = data;
        this.hora = hora;
        this.tipo = tipo;
        this.descricao = descricao;
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
    public Canal getCanal() { return canal; }
    public boolean isBuscaMoto() { return buscaMoto; }
    public String getEnderecoBusca() { return enderecoBusca; }
}
