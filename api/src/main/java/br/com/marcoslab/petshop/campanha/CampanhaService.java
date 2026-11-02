package br.com.marcoslab.petshop.campanha;

import br.com.marcoslab.petshop.auditoria.Auditoria.Acao;
import br.com.marcoslab.petshop.auditoria.AuditoriaService;
import br.com.marcoslab.petshop.cliente.Tutor;
import br.com.marcoslab.petshop.cliente.TutorRepository;
import br.com.marcoslab.petshop.erro.Erros;
import br.com.marcoslab.petshop.loja.Loja;
import br.com.marcoslab.petshop.loja.LojaRepository;
import br.com.marcoslab.petshop.whatsapp.MensagemRepository;
import br.com.marcoslab.petshop.whatsapp.MensagemWhatsApp;
import br.com.marcoslab.petshop.whatsapp.MensagemWhatsApp.Tipo;
import br.com.marcoslab.petshop.whatsapp.WhatsAppService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Campanhas de promoção SOMENTE para clientes da base que aceitaram receber (LGPD), com link de descadastro em toda
 * mensagem e limite por dia (a Meta limita números novos). Não existe envio para números avulsos ou listas compradas.
 */
@Service
public class CampanhaService {

    private static final Logger log = LoggerFactory.getLogger(CampanhaService.class);

    public record Relatorio(Long id, String titulo, String texto, Campanha.Status status, Instant criadaEm,
                            Map<EnvioCampanha.Status, Long> envios) {
    }

    private final CampanhaRepository campanhas;
    private final EnvioCampanhaRepository envios;
    private final TutorRepository tutores;
    private final LojaRepository lojas;
    private final WhatsAppService whatsapp;
    private final MensagemRepository mensagens;
    private final AuditoriaService auditoria;
    private final Clock clock;
    private final int limitePorDia;
    private final String siteUrl;
    private final String slug;

    public CampanhaService(CampanhaRepository campanhas, EnvioCampanhaRepository envios, TutorRepository tutores,
                           LojaRepository lojas, WhatsAppService whatsapp, MensagemRepository mensagens, AuditoriaService auditoria,
                           Clock clock, @Value("${app.campanhas.limite-dia:200}") int limitePorDia,
                           @Value("${app.site-url:http://localhost:3001}") String siteUrl,
                           @Value("${app.painel.loja-slug}") String slug) {
        this.campanhas = campanhas;
        this.envios = envios;
        this.tutores = tutores;
        this.lojas = lojas;
        this.whatsapp = whatsapp;
        this.mensagens = mensagens;
        this.auditoria = auditoria;
        this.clock = clock;
        this.limitePorDia = limitePorDia;
        this.siteUrl = siteUrl;
        this.slug = slug;
    }

    @Transactional(readOnly = true)
    public int publico(Loja loja) {
        return tutores.aceitaramPromocoes(loja.getId()).size();
    }

    /** Cria a campanha com a lista de quem aceitou AGORA e já envia até o limite do dia. */
    @Transactional
    public Relatorio criar(Loja loja, String titulo, String texto, String template) {
        List<Tutor> publico = tutores.aceitaramPromocoes(loja.getId());
        if (publico.isEmpty()) {
            throw new Erros.Conflito("Nenhum cliente aceitou receber promoções ainda. A campanha só vai para quem aceitou (LGPD).");
        }
        Campanha c = campanhas.save(new Campanha(loja, titulo.trim(), texto.trim(), template.trim(), clock.instant()));
        publico.forEach(t -> envios.save(new EnvioCampanha(c, t)));
        auditoria.registrar(loja, Acao.CRIOU, "campanha", c.getId(), null, c.getTitulo() + " · " + publico.size() + " clientes");
        enviarPendentes(loja, c);
        return relatorio(c);
    }

    /** Todo dia às 10h continua as campanhas que pararam no limite do dia. */
    @Scheduled(cron = "${app.campanhas.cron:0 0 10 * * *}", zone = "America/Sao_Paulo")
    @Transactional
    public void continuar() {
        lojas.findBySlug(slug).ifPresent(loja ->
                campanhas.findByLojaIdAndStatusOrderByCriadaEm(loja.getId(), Campanha.Status.ENVIANDO)
                        .forEach(c -> enviarPendentes(loja, c)));
    }

    private void enviarPendentes(Loja loja, Campanha c) {
        Instant inicioDoDia = LocalDate.now(clock).atStartOfDay(clock.getZone()).toInstant();
        long restante = limitePorDia - mensagens.contarTipo(loja.getId(), Tipo.CAMPANHA, inicioDoDia);
        for (EnvioCampanha e : campanhas.pendentes(c.getId())) {
            Tutor t = e.getTutor();
            if (t.getConsentimentoMarketingEm() == null || t.getWhatsapp().isBlank()) { // saiu da lista no meio do caminho
                e.marcar(EnvioCampanha.Status.PULADO, clock.instant());
                continue;
            }
            if (restante <= 0) return; // continua amanhã
            if (t.getTokenDescadastro() == null) t.aceitarPromocoes(t.getConsentimentoMarketingEm()); // aceite antigo sem link
            String sair = siteUrl + "/sair?t=" + t.getTokenDescadastro();
            String texto = "%s\n\nNão quer mais receber promoções? %s".formatted(c.getTexto(), sair);
            MensagemWhatsApp m = whatsapp.enviarTemplate(loja, t.getWhatsapp(), Tipo.CAMPANHA, texto, c.getTemplate(),
                    List.of(loja.getNome(), c.getTexto(), sair), "CAMPANHA:" + c.getId() + ":" + t.getId());
            e.marcar(switch (m.getStatus()) {
                case ENVIADA -> EnvioCampanha.Status.ENVIADA;
                case SIMULADA -> EnvioCampanha.Status.SIMULADA;
                case FALHOU -> EnvioCampanha.Status.FALHOU;
            }, clock.instant());
            restante--;
        }
        c.concluir();
        log.info("Campanha {} concluída", c.getId());
    }

    @Transactional(readOnly = true)
    public List<Relatorio> listar(Loja loja) {
        return campanhas.findTop50ByLojaIdOrderByCriadaEmDescIdDesc(loja.getId()).stream().map(this::relatorio).toList();
    }

    private Relatorio relatorio(Campanha c) {
        Map<EnvioCampanha.Status, Long> mapa = new EnumMap<>(EnvioCampanha.Status.class);
        for (EnvioCampanha.Status s : EnvioCampanha.Status.values()) mapa.put(s, 0L);
        for (Object[] linha : campanhas.contagem(c.getId())) mapa.put((EnvioCampanha.Status) linha[0], (Long) linha[1]);
        return new Relatorio(c.getId(), c.getTitulo(), c.getTexto(), c.getStatus(), c.getCriadaEm(), mapa);
    }

    /** Link "não quero mais receber": sempre responde igual, exista ou não o token (não dá para testar tokens). */
    @Transactional
    public void descadastrar(String token) {
        if (token == null || token.length() != 32) return;
        tutores.findByTokenDescadastro(token).ifPresent(t -> {
            if (t.getConsentimentoMarketingEm() == null) return;
            t.sairDasPromocoes(clock.instant());
            auditoria.registrar(t.getLoja(), Acao.EXCLUIU, "consentimento-promocoes", t.getId(), null,
                    "cliente #" + t.getId() + " saiu das promoções pelo link");
        });
    }
}
