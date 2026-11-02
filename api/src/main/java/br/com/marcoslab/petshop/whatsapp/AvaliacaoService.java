package br.com.marcoslab.petshop.whatsapp;

import br.com.marcoslab.petshop.agenda.Agendamento;
import br.com.marcoslab.petshop.cliente.Tutor;
import br.com.marcoslab.petshop.loja.Loja;
import br.com.marcoslab.petshop.whatsapp.MensagemWhatsApp.Tipo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.util.List;

/** Pede avaliação no Google depois de um atendimento concluído (se a loja ligou), no máximo 1 vez a cada 90 dias por cliente. */
@Service
public class AvaliacaoService {

    private final WhatsAppService whatsapp;
    private final MensagemRepository mensagens;
    private final Clock clock;
    private final String template;

    public AvaliacaoService(WhatsAppService whatsapp, MensagemRepository mensagens, Clock clock,
                            @Value("${app.whatsapp.template-avaliacao:avaliacao_atendimento}") String template) {
        this.whatsapp = whatsapp;
        this.mensagens = mensagens;
        this.clock = clock;
        this.template = template;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void depoisDoAtendimento(Loja loja, Agendamento a) {
        Tutor t = a.getPet().getTutor();
        if (!loja.isPedirAvaliacao() || loja.getLinkAvaliacao() == null || t.getWhatsapp().isBlank()) return;
        String para = WhatsAppService.numero(t.getWhatsapp());
        if (mensagens.enviadaDesde(loja.getId(), para, Tipo.AVALIACAO, clock.instant().minus(Duration.ofDays(90)))) return;
        String nome = t.getNome().trim().split("\\s+")[0];
        String texto = "Olá, %s! Obrigado por trazer o %s à %s. Se puder, avalie nosso atendimento no Google: %s"
                .formatted(nome, a.getPet().getNome(), loja.getNome(), loja.getLinkAvaliacao());
        whatsapp.enviarTemplate(loja, t.getWhatsapp(), Tipo.AVALIACAO, texto, template,
                List.of(loja.getNome(), loja.getLinkAvaliacao()), "AVALIACAO:" + a.getId());
    }
}
