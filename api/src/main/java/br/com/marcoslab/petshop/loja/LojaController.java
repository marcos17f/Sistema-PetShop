package br.com.marcoslab.petshop.loja;

import br.com.marcoslab.petshop.auditoria.Auditoria.Acao;
import br.com.marcoslab.petshop.auditoria.AuditoriaService;
import jakarta.validation.Valid;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import static br.com.marcoslab.petshop.cliente.ClienteDtos.vazioParaNull;

/** Configurações da loja (painel) e dados públicos para o site. */
@RestController
public class LojaController {

    private final LojaService lojas;
    private final LojaRepository repositorio;
    private final AuditoriaService auditoria;

    public LojaController(LojaService lojas, LojaRepository repositorio, AuditoriaService auditoria) {
        this.lojas = lojas;
        this.repositorio = repositorio;
        this.auditoria = auditoria;
    }

    @GetMapping("/api/lojas/{slug}/config")
    @Transactional(readOnly = true)
    public ConfigLoja config(@PathVariable String slug) {
        return ConfigLoja.de(lojas.buscar(slug));
    }

    @PutMapping("/api/lojas/{slug}/config")
    @Transactional
    public ConfigLoja salvar(@PathVariable String slug, @Valid @RequestBody ConfigLoja c) {
        Loja loja = repositorio.findById(lojas.buscar(slug).getId()).orElseThrow();
        String antes = ConfigLoja.de(loja).toString();
        loja.configurar(c.nome(), c.whatsapp(), vazioParaNull(c.cidade()), vazioParaNull(c.endereco()),
                vazioParaNull(c.horarioTexto()), vazioParaNull(c.instagram()), vazioParaNull(c.linkAvaliacao()),
                c.intervaloAgendaMinutos(), c.abre(), c.fecha(), c.sabadoAbre(), c.sabadoFecha());
        auditoria.registrar(loja, Acao.ALTEROU, "configuracoes", loja.getId(), antes, ConfigLoja.de(loja).toString());
        return ConfigLoja.de(loja);
    }

    /** Sem login: o site público mostra nome, contato e horários. Não há dado de cliente aqui. */
    @GetMapping("/api/publico/lojas/{slug}")
    @Transactional(readOnly = true)
    public ConfigLoja publico(@PathVariable String slug) {
        return ConfigLoja.de(lojas.buscarPublica(slug));
    }
}
