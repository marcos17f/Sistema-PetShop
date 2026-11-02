package br.com.marcoslab.petshop.loja;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalTime;

/** Loja e as configurações que o dono edita no painel (tela Configurações). */
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

    /** Só dígitos, com DDD. */
    @Column(length = 20)
    private String whatsapp;

    private String cidade;
    private String endereco;

    @Column(name = "horario_texto")
    private String horarioTexto;

    /** Usuário do Instagram, sem @. */
    private String instagram;

    /** Link do perfil da empresa no Google para pedir avaliação. */
    @Column(name = "link_avaliacao")
    private String linkAvaliacao;

    @Column(name = "intervalo_agenda_minutos", nullable = false)
    private int intervaloAgendaMinutos = 40;

    /** Segunda a sexta. */
    @Column(nullable = false)
    private LocalTime abre = LocalTime.of(8, 0);

    @Column(nullable = false)
    private LocalTime fecha = LocalTime.of(18, 0);

    /** Sábado; null = fechado. Domingo é sempre fechado. */
    @Column(name = "sabado_abre")
    private LocalTime sabadoAbre;

    @Column(name = "sabado_fecha")
    private LocalTime sabadoFecha;

    protected Loja() {
    }

    public void configurar(String nome, String whatsapp, String cidade, String endereco, String horarioTexto, String instagram,
                           String linkAvaliacao, int intervaloAgendaMinutos, LocalTime abre, LocalTime fecha,
                           LocalTime sabadoAbre, LocalTime sabadoFecha) {
        this.nome = nome.trim();
        this.whatsapp = whatsapp.replaceAll("\\D", "");
        this.cidade = cidade;
        this.endereco = endereco;
        this.horarioTexto = horarioTexto;
        this.instagram = instagram;
        this.linkAvaliacao = linkAvaliacao;
        this.intervaloAgendaMinutos = intervaloAgendaMinutos;
        this.abre = abre;
        this.fecha = fecha;
        this.sabadoAbre = sabadoAbre;
        this.sabadoFecha = sabadoFecha;
    }

    public Long getId() { return id; }
    public String getSlug() { return slug; }
    public String getNome() { return nome; }
    public String getWhatsapp() { return whatsapp; }
    public String getCidade() { return cidade; }
    public String getEndereco() { return endereco; }
    public String getHorarioTexto() { return horarioTexto; }
    public String getInstagram() { return instagram; }
    public String getLinkAvaliacao() { return linkAvaliacao; }
    public int getIntervaloAgendaMinutos() { return intervaloAgendaMinutos; }
    public LocalTime getAbre() { return abre; }
    public LocalTime getFecha() { return fecha; }
    public LocalTime getSabadoAbre() { return sabadoAbre; }
    public LocalTime getSabadoFecha() { return sabadoFecha; }
}
