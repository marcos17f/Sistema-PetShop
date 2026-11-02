package br.com.marcoslab.petshop.online;

import br.com.marcoslab.petshop.agenda.AgendaService;
import br.com.marcoslab.petshop.agenda.TipoServico;
import br.com.marcoslab.petshop.financeiro.PrecoServico;
import br.com.marcoslab.petshop.financeiro.PrecoServicoRepository;
import br.com.marcoslab.petshop.loja.Loja;
import br.com.marcoslab.petshop.loja.LojaService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Rotas públicas (sem login) da página /agendar. Nenhuma devolve dados de clientes. */
@RestController
@RequestMapping("/api/publico/lojas/{slug}")
public class AgendamentoOnlineController {

    public record Servico(TipoServico tipo, String nome, BigDecimal preco) {
    }

    private final LojaService lojas;
    private final AgendaService agenda;
    private final PrecoServicoRepository precos;
    private final AgendamentoOnlineService online;

    public AgendamentoOnlineController(LojaService lojas, AgendaService agenda, PrecoServicoRepository precos,
                                       AgendamentoOnlineService online) {
        this.lojas = lojas;
        this.agenda = agenda;
        this.precos = precos;
        this.online = online;
    }

    @GetMapping("/servicos")
    public List<Servico> servicos(@PathVariable String slug) {
        Loja loja = lojas.buscarPublica(slug);
        Map<TipoServico, BigDecimal> tabela = precos.findByLojaId(loja.getId()).stream()
                .collect(Collectors.toMap(PrecoServico::getTipoServico, PrecoServico::getValor));
        return Arrays.stream(TipoServico.values()).map(t -> new Servico(t, nomeExibicao(t), tabela.get(t))).toList();
    }

    @GetMapping("/horarios")
    public List<String> horarios(@PathVariable String slug,
                                 @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data) {
        return agenda.horariosLivres(lojas.buscarPublica(slug), data).stream().map(LocalTime::toString).toList();
    }

    @PostMapping("/agendamentos")
    @ResponseStatus(HttpStatus.CREATED)
    public AgendamentoOnlineService.Confirmacao agendar(@PathVariable String slug, @Valid @RequestBody PedidoOnline pedido,
                                                        HttpServletRequest req) {
        return online.agendar(lojas.buscarPublica(slug), pedido, req.getRemoteAddr());
    }

    private static String nomeExibicao(TipoServico t) {
        String n = t.nome();
        return Character.toUpperCase(n.charAt(0)) + n.substring(1);
    }
}
