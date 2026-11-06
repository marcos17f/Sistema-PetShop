package br.com.marcoslab.petshop.lembrete;

import br.com.marcoslab.petshop.agenda.Agendamento;
import br.com.marcoslab.petshop.agenda.AgendamentoRepository;
import br.com.marcoslab.petshop.agenda.StatusAgendamento;
import br.com.marcoslab.petshop.cliente.Pet;
import br.com.marcoslab.petshop.cliente.PetRepository;
import br.com.marcoslab.petshop.cliente.Tutor;
import br.com.marcoslab.petshop.loja.Loja;
import br.com.marcoslab.petshop.vacina.AplicacaoVacina;
import br.com.marcoslab.petshop.vacina.VacinaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Calcula os lembretes de uma data. As mensagens falam "amanhã" porque o painel e o envio
 * automático (18h) trabalham com os lembretes do dia seguinte. Mesmas regras de site/src/lib/demo.ts.
 */
@Service
public class LembreteService {

    private static final DateTimeFormatter DIA_MES = DateTimeFormatter.ofPattern("dd/MM");
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    private final AgendamentoRepository agenda;
    private final VacinaRepository vacinas;
    private final PetRepository pets;
    private final LembreteEnviadoRepository enviados;
    private final Clock clock;

    public LembreteService(AgendamentoRepository agenda, VacinaRepository vacinas, PetRepository pets,
                           LembreteEnviadoRepository enviados, Clock clock) {
        this.agenda = agenda;
        this.vacinas = vacinas;
        this.pets = pets;
        this.enviados = enviados;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<Lembrete> doDia(Loja loja, LocalDate data) {
        List<Lembrete> lista = new ArrayList<>();

        for (Agendamento a : agenda.doDia(loja.getId(), data)) {
            if (a.getStatus() == StatusAgendamento.CANCELADO) continue;
            Pet pet = a.getPet();
            Tutor t = pet.getTutor();
            lista.add(novo("AGENDAMENTO:" + a.getId() + ":" + data, Lembrete.Tipo.AGENDAMENTO, pet, data,
                    "Olá, %s! Aqui é da %s. Lembrete: amanhã (%s) às %s o %s tem %s marcado. Podemos confirmar?".formatted(
                            primeiroNome(t), loja.getNome(), data.format(DIA_MES), a.getHora().format(HORA), pet.getNome(), a.getTipo().nome())));
        }

        for (AplicacaoVacina v : vacinas.vencendoEm(loja.getId(), data)) {
            Pet pet = v.getPet();
            String dose = v.getDose() == null ? "" : " (" + v.getDose() + ")";
            lista.add(novo("VACINA:" + v.getId() + ":" + data, Lembrete.Tipo.VACINA, pet, data,
                    "Olá, %s! Aqui é da %s. Amanhã (%s) é dia da vacina %s%s do %s. Vamos agendar o horário?".formatted(
                            primeiroNome(pet.getTutor()), loja.getNome(), data.format(DIA_MES), v.getVacina(), dose, pet.getNome())));
        }

        for (Pet pet : pets.comRetornoDeBanho(loja.getId())) {
            if (!Objects.equals(pet.proximoBanho(), data)) continue;
            lista.add(novo("BANHO:" + pet.getId() + ":" + data, Lembrete.Tipo.BANHO, pet, data,
                    "Olá, %s! Aqui é da %s. Amanhã completa %d dias do último banho do %s. Quer agendar o próximo?".formatted(
                            primeiroNome(pet.getTutor()), loja.getNome(), pet.getIntervaloBanhoDias(), pet.getNome())));
        }

        if (lista.isEmpty()) return lista;
        Set<String> jaEnviadas = enviados.jaEnviadas(loja.getId(), lista.stream().map(Lembrete::chave).toList());
        return lista.stream().map(l -> l.comEnviado(jaEnviadas.contains(l.chave()))).toList();
    }

    /** Marca como enviado. Idempotente: clicar de novo não duplica (e a tabela tem chave única). */
    @Transactional
    public void marcarEnviado(Loja loja, String chave) {
        if (enviados.existsByLojaIdAndChave(loja.getId(), chave)) return;
        enviados.save(new LembreteEnviado(loja, chave, clock.instant()));
    }

    private static Lembrete novo(String chave, Lembrete.Tipo tipo, Pet pet, LocalDate data, String mensagem) {
        Tutor t = pet.getTutor();
        return new Lembrete(chave, tipo, pet.getId(), pet.getNome(), t.getNome(), t.getWhatsapp(), data, mensagem, false);
    }

    private static String primeiroNome(Tutor t) {
        return t.getNome().trim().split("\\s+")[0];
    }
}
