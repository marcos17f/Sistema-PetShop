package br.com.marcoslab.petshop.whatsapp;

import br.com.marcoslab.petshop.loja.Loja;
import br.com.marcoslab.petshop.loja.LojaRepository;
import br.com.marcoslab.petshop.loja.LojaService;
import br.com.marcoslab.petshop.whatsapp.MensagemWhatsApp.Status;
import br.com.marcoslab.petshop.whatsapp.MensagemWhatsApp.Tipo;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

import static br.com.marcoslab.petshop.cliente.ClienteDtos.vazioParaNull;

/** Tela WhatsApp do painel: modo (simulado/oficial), preferências, teste e registro das mensagens. */
@RestController
@RequestMapping("/api/lojas/{slug}/whatsapp")
public class WhatsAppController {

    public record Situacao(boolean oficial, boolean lembretesAutomaticos, boolean respostaRapida, String textoRespostaRapida,
                           boolean pedirAvaliacao, boolean temLinkAvaliacao) {
    }

    public record Preferencias(boolean lembretesAutomaticos, boolean respostaRapida,
                               @Size(max = 500, message = "Use no máximo 500 caracteres") String textoRespostaRapida,
                               boolean pedirAvaliacao) {
    }

    public record MensagemDto(Long id, String para, Tipo tipo, String texto, Status status, String erro, Instant criadoEm) {
        static MensagemDto de(MensagemWhatsApp m) {
            return new MensagemDto(m.getId(), m.getPara(), m.getTipo(), m.getTexto(), m.getStatus(), m.getErro(), m.getCriadoEm());
        }
    }

    private final LojaService lojas;
    private final LojaRepository repositorio;
    private final WhatsAppService whatsapp;

    public WhatsAppController(LojaService lojas, LojaRepository repositorio, WhatsAppService whatsapp) {
        this.lojas = lojas;
        this.repositorio = repositorio;
        this.whatsapp = whatsapp;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public Situacao situacao(@PathVariable String slug) {
        Loja l = lojas.buscar(slug);
        return new Situacao(whatsapp.oficial(), l.isLembretesAutomaticos(), l.isRespostaRapida(),
                l.getTextoRespostaRapida() == null ? "" : l.getTextoRespostaRapida(), l.isPedirAvaliacao(),
                l.getLinkAvaliacao() != null);
    }

    @PutMapping
    @Transactional
    public Situacao preferencias(@PathVariable String slug, @Valid @RequestBody Preferencias p) {
        Loja l = repositorio.findById(lojas.buscar(slug).getId()).orElseThrow();
        l.configurarWhatsapp(p.lembretesAutomaticos(), p.respostaRapida(), vazioParaNull(p.textoRespostaRapida()), p.pedirAvaliacao());
        return situacao(slug);
    }

    @GetMapping("/mensagens")
    public List<MensagemDto> mensagens(@PathVariable String slug) {
        return whatsapp.ultimas(lojas.buscar(slug)).stream().map(MensagemDto::de).toList();
    }

    /** Manda (ou simula) uma mensagem de teste para o WhatsApp da própria loja. */
    @PostMapping("/teste")
    public MensagemDto teste(@PathVariable String slug) {
        Loja l = lojas.buscar(slug);
        String texto = "Teste do sistema " + l.getNome() + ": o WhatsApp está funcionando.";
        return MensagemDto.de(whatsapp.enviarTexto(l, l.getWhatsapp() == null ? "" : l.getWhatsapp(), Tipo.TESTE, texto));
    }

    /** Roda agora o mesmo envio das 18h (útil para testar). */
    @PostMapping("/lembretes-agora")
    public WhatsAppService.Resultado lembretesAgora(@PathVariable String slug) {
        return whatsapp.lembretesDeAmanha(lojas.buscar(slug));
    }
}
