package br.com.marcoslab.petshop.lembrete;

import br.com.marcoslab.petshop.loja.Loja;
import br.com.marcoslab.petshop.loja.LojaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;

/** Todo dia às 18h (horário de Goiânia) dispara os lembretes de amanhã, se o envio automático estiver ligado. */
@Component
public class LembreteAgendador {

    private static final Logger log = LoggerFactory.getLogger(LembreteAgendador.class);

    private final LembreteService lembretes;
    private final LojaRepository lojas;
    private final NotificadorWhatsApp notificador;
    private final Clock clock;
    private final boolean automatico;
    private final String slug;

    public LembreteAgendador(LembreteService lembretes, LojaRepository lojas, NotificadorWhatsApp notificador, Clock clock,
                             @Value("${app.whatsapp.automatico}") boolean automatico,
                             @Value("${app.painel.loja-slug}") String slug) {
        this.lembretes = lembretes;
        this.lojas = lojas;
        this.notificador = notificador;
        this.clock = clock;
        this.automatico = automatico;
        this.slug = slug;
    }

    @Scheduled(cron = "${app.lembrete.cron}", zone = "America/Sao_Paulo")
    public void dispararAmanha() {
        if (!automatico) return;
        Loja loja = lojas.findBySlug(slug).orElse(null);
        if (loja == null) return;
        LocalDate amanha = LocalDate.now(clock).plusDays(1);
        int enviados = 0;
        for (Lembrete l : lembretes.doDia(loja, amanha)) {
            if (l.enviado()) continue;
            try {
                if (notificador.enviar(l.whatsapp(), l.mensagem())) {
                    lembretes.marcarEnviado(loja, l.chave());
                    enviados++;
                }
            } catch (RuntimeException e) {
                log.warn("Falha ao enviar lembrete {}: {}", l.chave(), e.getMessage());
            }
        }
        log.info("Lembretes de {}: {} enviados automaticamente", amanha, enviados);
    }
}
