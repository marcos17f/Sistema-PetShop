package br.com.marcoslab.petshop.lembrete;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class NotificadorLog implements NotificadorWhatsApp {

    private static final Logger log = LoggerFactory.getLogger(NotificadorLog.class);

    @Override
    public boolean enviar(String whatsapp, String mensagem) {
        log.info("[WhatsApp simulado] para {}: {}", whatsapp, mensagem);
        return false; // não enviou de verdade: o lembrete continua pendente no painel
    }
}
