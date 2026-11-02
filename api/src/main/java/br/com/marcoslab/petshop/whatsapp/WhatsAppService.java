package br.com.marcoslab.petshop.whatsapp;

import br.com.marcoslab.petshop.lembrete.Lembrete;
import br.com.marcoslab.petshop.lembrete.LembreteService;
import br.com.marcoslab.petshop.loja.Loja;
import br.com.marcoslab.petshop.whatsapp.MensagemWhatsApp.Status;
import br.com.marcoslab.petshop.whatsapp.MensagemWhatsApp.Tipo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

/**
 * Envia mensagens de WhatsApp e registra todas. Sem a API oficial configurada, fica no modo SIMULADO:
 * nada sai, e a tela WhatsApp do painel mostra cada mensagem com o botão para mandar por clique.
 * LGPD: o log nunca leva telefone nem texto, só o id da mensagem.
 */
@Service
public class WhatsAppService {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppService.class);

    public record Resultado(int enviadas, int simuladas, int falhas, int jaFeitas) {
    }

    private final WhatsAppCloud cloud;
    private final MensagemRepository mensagens;
    private final LembreteService lembretes;
    private final Clock clock;
    private final String templateLembrete;

    public WhatsAppService(WhatsAppCloud cloud, MensagemRepository mensagens, LembreteService lembretes, Clock clock,
                           @Value("${app.whatsapp.template-lembrete:lembrete_pet}") String templateLembrete) {
        this.cloud = cloud;
        this.mensagens = mensagens;
        this.lembretes = lembretes;
        this.clock = clock;
        this.templateLembrete = templateLembrete;
    }

    public boolean oficial() {
        return cloud.configurado();
    }

    /** Mensagem de modelo aprovado (lembrete, confirmação, campanha...): pode ir a qualquer hora. */
    @Transactional
    public MensagemWhatsApp enviarTemplate(Loja loja, String para, Tipo tipo, String texto, String template, List<String> variaveis,
                                           String chave) {
        MensagemWhatsApp m = mensagens.save(new MensagemWhatsApp(loja, numero(para), tipo, texto, template, chave, clock.instant()));
        if (oficial()) tentar(m, () -> cloud.enviarTemplate(m.getPara(), template, variaveis));
        return m;
    }

    /** Texto livre: só vale dentro de 24 h da última mensagem do cliente (resposta rápida). */
    @Transactional
    public MensagemWhatsApp enviarTexto(Loja loja, String para, Tipo tipo, String texto) {
        MensagemWhatsApp m = mensagens.save(new MensagemWhatsApp(loja, numero(para), tipo, texto, null, null, clock.instant()));
        if (oficial()) tentar(m, () -> cloud.enviarTexto(m.getPara(), texto));
        return m;
    }

    /** Lembretes de amanhã. Cada lembrete sai uma vez só; enviado de verdade fica marcado também na tela Lembretes. */
    @Transactional
    public Resultado lembretesDeAmanha(Loja loja) {
        LocalDate amanha = LocalDate.now(clock).plusDays(1);
        int enviadas = 0, simuladas = 0, falhas = 0, jaFeitas = 0;
        for (Lembrete l : lembretes.doDia(loja, amanha)) {
            if (l.enviado() || l.whatsapp().isBlank() || mensagens.jaTem(loja.getId(), l.chave())) {
                jaFeitas++;
                continue;
            }
            MensagemWhatsApp m = enviarTemplate(loja, l.whatsapp(), Tipo.LEMBRETE, l.mensagem(), templateLembrete,
                    List.of(loja.getNome(), l.mensagem()), l.chave());
            switch (m.getStatus()) {
                case ENVIADA -> { lembretes.marcarEnviado(loja, l.chave()); enviadas++; }
                case SIMULADA -> simuladas++;
                case FALHOU -> falhas++;
            }
        }
        log.info("Lembretes de {}: {} enviados, {} simulados, {} falhas", amanha, enviadas, simuladas, falhas);
        return new Resultado(enviadas, simuladas, falhas, jaFeitas);
    }

    @Transactional(readOnly = true)
    public List<MensagemWhatsApp> ultimas(Loja loja) {
        return mensagens.findTop100ByLojaIdOrderByCriadoEmDescIdDesc(loja.getId());
    }

    /** LGPD: o registro de mensagens não fica para sempre. */
    @Scheduled(cron = "${app.whatsapp.limpeza-cron:0 30 3 * * *}", zone = "America/Sao_Paulo")
    @Transactional
    public void apagarAntigas() {
        int n = mensagens.apagarAntesDe(clock.instant().minus(Duration.ofDays(90)));
        if (n > 0) log.info("{} mensagens de WhatsApp com mais de 90 dias apagadas", n);
    }

    private void tentar(MensagemWhatsApp m, java.util.function.Supplier<String> envio) {
        try {
            m.enviada(envio.get());
        } catch (RestClientResponseException e) {
            m.falhou("Meta respondeu " + e.getStatusCode().value() + ": " + e.getResponseBodyAsString());
            log.warn("WhatsApp: mensagem {} falhou ({})", m.getId(), e.getStatusCode().value());
        } catch (RestClientException e) {
            m.falhou("Sem conexão com a Meta");
            log.warn("WhatsApp: mensagem {} falhou (conexão)", m.getId());
        }
        if (m.getStatus() == Status.ENVIADA) log.info("WhatsApp: mensagem {} enviada", m.getId());
    }

    /** Só dígitos, com 55 (Brasil) na frente quando vier só DDD + número. */
    static String numero(String whatsapp) {
        String d = whatsapp.replaceAll("\\D", "");
        return d.length() == 10 || d.length() == 11 ? "55" + d : d;
    }
}
