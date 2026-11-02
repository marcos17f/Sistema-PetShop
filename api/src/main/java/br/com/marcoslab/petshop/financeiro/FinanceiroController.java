package br.com.marcoslab.petshop.financeiro;

import br.com.marcoslab.petshop.agenda.AgendaController.AgendamentoDto;
import br.com.marcoslab.petshop.agenda.TipoServico;
import br.com.marcoslab.petshop.auditoria.AuditoriaService;
import br.com.marcoslab.petshop.financeiro.FinanceiroDtos.*;
import br.com.marcoslab.petshop.loja.LojaService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.YearMonth;
import java.util.List;

/** Custos fixos e variáveis, receitas, resumo do mês e preço padrão dos serviços. Mês sempre como yyyy-MM. */
@RestController
@RequestMapping("/api/lojas/{slug}")
public class FinanceiroController {

    private final LojaService lojas;
    private final FinanceiroService financeiro;

    public FinanceiroController(LojaService lojas, FinanceiroService financeiro) {
        this.lojas = lojas;
        this.financeiro = financeiro;
    }

    @GetMapping("/financeiro/categorias")
    public List<CategoriaDto> categorias(@PathVariable String slug) {
        return financeiro.categorias(lojas.buscar(slug));
    }

    @PostMapping("/financeiro/categorias")
    @ResponseStatus(HttpStatus.CREATED)
    public CategoriaDto criarCategoria(@PathVariable String slug, @Valid @RequestBody NovaCategoria dados) {
        return financeiro.criarCategoria(lojas.buscar(slug), dados);
    }

    @GetMapping("/financeiro/lancamentos")
    public List<LancamentoDto> lancamentos(@PathVariable String slug, @RequestParam YearMonth mes,
                                           @RequestParam(required = false) TipoLancamento tipo) {
        return financeiro.lancamentos(lojas.buscar(slug), mes, tipo);
    }

    @PostMapping("/financeiro/lancamentos")
    @ResponseStatus(HttpStatus.CREATED)
    public LancamentoDto criar(@PathVariable String slug, @Valid @RequestBody DadosLancamento dados) {
        return financeiro.criar(lojas.buscar(slug), dados);
    }

    @PutMapping("/financeiro/lancamentos/{id}")
    public LancamentoDto alterar(@PathVariable String slug, @PathVariable Long id, @Valid @RequestBody DadosLancamento dados) {
        return financeiro.alterar(lojas.buscar(slug), id, dados);
    }

    @DeleteMapping("/financeiro/lancamentos/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(@PathVariable String slug, @PathVariable Long id) {
        financeiro.excluir(lojas.buscar(slug), id);
    }

    /** Atendimentos concluídos do mês (receitas de serviço). */
    @GetMapping("/financeiro/atendimentos")
    @Transactional(readOnly = true)
    public List<AgendamentoDto> atendimentos(@PathVariable String slug, @RequestParam YearMonth mes) {
        return financeiro.atendimentos(lojas.buscar(slug), mes).stream().map(AgendamentoDto::de).toList();
    }

    @GetMapping("/financeiro/resumo")
    public Resumo resumo(@PathVariable String slug, @RequestParam YearMonth mes) {
        return financeiro.resumo(lojas.buscar(slug), mes);
    }

    /** Últimas 100 alterações. Só leitura: não existe rota para editar nem apagar. */
    @GetMapping("/financeiro/historico")
    public List<AuditoriaService.AuditoriaDto> historico(@PathVariable String slug) {
        return financeiro.historico(lojas.buscar(slug));
    }

    @GetMapping("/precos")
    public List<PrecoDto> precos(@PathVariable String slug) {
        return financeiro.precos(lojas.buscar(slug));
    }

    @PutMapping("/precos/{tipo}")
    public PrecoDto definirPreco(@PathVariable String slug, @PathVariable TipoServico tipo, @Valid @RequestBody NovoPreco dados) {
        return financeiro.definirPreco(lojas.buscar(slug), tipo, dados.valor());
    }
}
