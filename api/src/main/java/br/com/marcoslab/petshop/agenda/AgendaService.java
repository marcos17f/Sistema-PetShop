package br.com.marcoslab.petshop.agenda;

import br.com.marcoslab.petshop.cliente.Pet;
import br.com.marcoslab.petshop.erro.Erros;
import br.com.marcoslab.petshop.loja.Loja;
import br.com.marcoslab.petshop.loja.LojaService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Regras da agenda, as mesmas para o painel e para o agendamento online: um atendimento por vez com o intervalo da loja
 * (de qualquer cliente) e gravação um de cada vez por loja, para dois salvamentos simultâneos não passarem juntos.
 */
@Service
public class AgendaService {

    /** Agendamento online: no mínimo 1 h de antecedência e até 60 dias à frente. */
    static final Duration ANTECEDENCIA_ONLINE = Duration.ofHours(1);
    static final int DIAS_ONLINE = 60;

    private final AgendamentoRepository agenda;
    private final LojaService lojas;
    private final Clock clock;

    public AgendaService(AgendamentoRepository agenda, LojaService lojas, Clock clock) {
        this.agenda = agenda;
        this.lojas = lojas;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Agendamento agendar(Loja loja, Pet pet, LocalDate data, LocalTime hora, TipoServico tipo, String descricao,
                               boolean buscaMoto, String enderecoBusca, Canal canal) {
        lojas.travarAgenda(loja);
        if (canal == Canal.ONLINE && !horariosLivres(loja, data).contains(hora)) {
            throw new Erros.Conflito("Esse horário não está mais disponível. Escolha outro.");
        }
        garantirIntervalo(loja, data, hora, null);
        return agenda.save(new Agendamento(loja, pet, data, hora, tipo, descricao, buscaMoto, enderecoBusca, canal, clock.instant()));
    }

    /**
     * Horários que o cliente pode escolher no agendamento online: dentro do funcionamento da loja (domingo fechado),
     * de intervalo em intervalo, terminando até a hora de fechar, longe de outros atendimentos e com 1 h de antecedência.
     */
    @Transactional(readOnly = true)
    public List<LocalTime> horariosLivres(Loja loja, LocalDate data) {
        LocalDate hoje = LocalDate.now(clock);
        if (data.isBefore(hoje) || data.isAfter(hoje.plusDays(DIAS_ONLINE))) return List.of();
        LocalTime abre, fecha;
        switch (data.getDayOfWeek()) {
            case SUNDAY -> { return List.of(); }
            case SATURDAY -> { abre = loja.getSabadoAbre(); fecha = loja.getSabadoFecha(); }
            default -> { abre = loja.getAbre(); fecha = loja.getFecha(); }
        }
        if (abre == null || fecha == null) return List.of();
        int passo = loja.getIntervaloAgendaMinutos();
        LocalTime minimo = data.equals(hoje) ? LocalTime.now(clock).plus(ANTECEDENCIA_ONLINE) : LocalTime.MIN;
        List<Agendamento> ativos = agenda.ativosDoDia(loja.getId(), data);
        List<LocalTime> livres = new ArrayList<>();
        // conta em minutos do dia para não "dar a volta" depois da meia-noite
        int fim = fecha.toSecondOfDay() / 60;
        for (int m = abre.toSecondOfDay() / 60; m + passo <= fim; m += passo) {
            LocalTime t = LocalTime.of(m / 60, m % 60);
            if (t.isBefore(minimo)) continue;
            boolean perto = ativos.stream().anyMatch(o -> Math.abs(Duration.between(o.getHora(), t).toMinutes()) < passo);
            if (!perto) livres.add(t);
        }
        return livres;
    }

    /**
     * Entre um atendimento e outro precisa haver pelo menos o intervalo da loja (tela Configurações), de qualquer cliente.
     * Ex.: com 40 min e um banho às 09:00, só dá para marcar até 08:20 ou a partir de 09:40.
     */
    public void garantirIntervalo(Loja loja, LocalDate data, LocalTime hora, Long ignorar) {
        int intervaloMinutos = loja.getIntervaloAgendaMinutos();
        agenda.ativosDoDia(loja.getId(), data).stream()
                .filter(o -> !Objects.equals(o.getId(), ignorar))
                .filter(o -> Math.abs(Duration.between(o.getHora(), hora).toMinutes()) < intervaloMinutos)
                .findFirst()
                .ifPresent(o -> {
                    throw new Erros.Conflito("Muito perto do atendimento das %s. Deixe pelo menos %d minutos entre um atendimento e outro."
                            .formatted(o.getHora(), intervaloMinutos));
                });
    }
}
