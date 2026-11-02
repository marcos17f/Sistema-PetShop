package br.com.marcoslab.petshop.whatsapp;

import br.com.marcoslab.petshop.cliente.Tutor;
import br.com.marcoslab.petshop.cliente.TutorRepository;
import br.com.marcoslab.petshop.loja.Loja;
import br.com.marcoslab.petshop.loja.LojaRepository;
import br.com.marcoslab.petshop.whatsapp.MensagemWhatsApp.Tipo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.util.Locale;

/**
 * Mensagem que chegou no WhatsApp da loja: conversa nova vira cliente (com a origem) e, se a loja ligou,
 * recebe a resposta rápida com o link do agendamento online (uma vez a cada 24 h por número).
 */
@Service
public class MensagemRecebidaService {

    static final String RESPOSTA_PADRAO = "Oi! Recebemos sua mensagem e já vamos responder. Se preferir, agende agora pelo link: {link}";

    private final LojaRepository lojas;
    private final TutorRepository tutores;
    private final MensagemRepository mensagens;
    private final WhatsAppService whatsapp;
    private final Clock clock;
    private final String slug;
    private final String siteUrl;

    public MensagemRecebidaService(LojaRepository lojas, TutorRepository tutores, MensagemRepository mensagens,
                                   WhatsAppService whatsapp, Clock clock, @Value("${app.painel.loja-slug}") String slug,
                                   @Value("${app.site-url:http://localhost:3001}") String siteUrl) {
        this.lojas = lojas;
        this.tutores = tutores;
        this.mensagens = mensagens;
        this.whatsapp = whatsapp;
        this.clock = clock;
        this.slug = slug;
        this.siteUrl = siteUrl;
    }

    @Transactional
    public void recebida(String de, String nomePerfil, String texto) {
        Loja loja = lojas.findBySlug(slug).orElseThrow();
        String digitos = de.replaceAll("\\D", "");
        if (digitos.length() < 10) return;
        String local = digitos.startsWith("55") && digitos.length() >= 12 ? digitos.substring(2) : digitos;

        if (tutores.porWhatsapp(loja.getId(), local, "55" + local).isEmpty()) {
            String nome = nomePerfil == null || nomePerfil.isBlank() ? "Cliente do WhatsApp" : nomePerfil.trim();
            String origem = texto != null && texto.toLowerCase(Locale.ROOT).contains("instagram") ? "Instagram" : "WhatsApp";
            tutores.save(new Tutor(loja, nome.length() > 120 ? nome.substring(0, 120) : nome, local, null, null, origem, clock.instant()));
        }

        if (loja.isRespostaRapida()
                && !mensagens.enviadaDesde(loja.getId(), "55" + local, Tipo.RESPOSTA_RAPIDA, clock.instant().minus(Duration.ofHours(24)))) {
            String modelo = loja.getTextoRespostaRapida() == null ? RESPOSTA_PADRAO : loja.getTextoRespostaRapida();
            whatsapp.enviarTexto(loja, local, Tipo.RESPOSTA_RAPIDA, modelo.replace("{link}", siteUrl + "/agendar?origem=whatsapp"));
        }
    }
}
