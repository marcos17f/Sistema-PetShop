package br.com.marcoslab.petshop.lembrete;

import br.com.marcoslab.petshop.loja.LojaRepository;
import br.com.marcoslab.petshop.whatsapp.WhatsAppService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Todo dia às 18h (horário de Goiânia) manda os lembretes de amanhã, se a loja ligou isso na tela WhatsApp. */
@Component
public class LembreteAgendador {

    private final LojaRepository lojas;
    private final WhatsAppService whatsapp;
    private final String slug;

    public LembreteAgendador(LojaRepository lojas, WhatsAppService whatsapp, @Value("${app.painel.loja-slug}") String slug) {
        this.lojas = lojas;
        this.whatsapp = whatsapp;
        this.slug = slug;
    }

    @Scheduled(cron = "${app.lembrete.cron}", zone = "America/Sao_Paulo")
    public void dispararAmanha() {
        lojas.findBySlug(slug).filter(l -> l.isLembretesAutomaticos()).ifPresent(whatsapp::lembretesDeAmanha);
    }
}
