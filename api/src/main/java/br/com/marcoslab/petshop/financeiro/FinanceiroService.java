package br.com.marcoslab.petshop.financeiro;

import br.com.marcoslab.petshop.agenda.Agendamento;
import br.com.marcoslab.petshop.agenda.AgendamentoRepository;
import br.com.marcoslab.petshop.agenda.TipoServico;
import br.com.marcoslab.petshop.auditoria.Auditoria.Acao;
import br.com.marcoslab.petshop.auditoria.AuditoriaService;
import br.com.marcoslab.petshop.erro.Erros;
import br.com.marcoslab.petshop.financeiro.FinanceiroDtos.*;
import br.com.marcoslab.petshop.loja.Loja;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import static br.com.marcoslab.petshop.cliente.ClienteDtos.vazioParaNull;

@Service
public class FinanceiroService {

    private final CategoriaRepository categorias;
    private final LancamentoRepository lancamentos;
    private final PrecoServicoRepository precos;
    private final AgendamentoRepository agenda;
    private final AuditoriaService auditoria;
    private final Clock clock;

    public FinanceiroService(CategoriaRepository categorias, LancamentoRepository lancamentos, PrecoServicoRepository precos,
                             AgendamentoRepository agenda, AuditoriaService auditoria, Clock clock) {
        this.categorias = categorias;
        this.auditoria = auditoria;
        this.lancamentos = lancamentos;
        this.precos = precos;
        this.agenda = agenda;
        this.clock = clock;
    }

    /* ---------- categorias ---------- */

    @Transactional(readOnly = true)
    public List<CategoriaDto> categorias(Loja loja) {
        return categorias.daLoja(loja.getId()).stream().map(CategoriaDto::de).toList();
    }

    @Transactional
    public CategoriaDto criarCategoria(Loja loja, NovaCategoria dados) {
        String nome = dados.nome().trim();
        if (categorias.existe(loja.getId(), dados.tipo(), nome)) {
            throw new Erros.Conflito("Já existe uma categoria com esse nome.");
        }
        Categoria c = categorias.save(new Categoria(loja, nome, dados.tipo()));
        auditoria.registrar(loja, Acao.CRIOU, "categoria", c.getId(), null, c.getTipo() + " · " + c.getNome());
        return CategoriaDto.de(c);
    }

    /* ---------- lançamentos ---------- */

    @Transactional
    public List<LancamentoDto> lancamentos(Loja loja, YearMonth mes, TipoLancamento tipo) {
        garantirRecorrentes(loja, mes);
        return lancamentos.entre(loja.getId(), mes.atDay(1), mes.atEndOfMonth()).stream()
                .filter(l -> tipo == null || l.getTipo() == tipo)
                .map(LancamentoDto::de).toList();
    }

    @Transactional
    public LancamentoDto criar(Loja loja, DadosLancamento dados) {
        Lancamento l = new Lancamento(loja, categoriaDaLoja(loja, dados.categoriaId()), dados.data(),
                vazioParaNull(dados.descricao()), dados.valor(), dados.recorrente(), clock.instant());
        lancamentos.save(l);
        auditoria.registrar(loja, Acao.CRIOU, "lancamento", l.getId(), null, descrever(l));
        return LancamentoDto.de(l);
    }

    @Transactional
    public LancamentoDto alterar(Loja loja, Long id, DadosLancamento dados) {
        Lancamento l = lancamentoDaLoja(loja, id);
        String antes = descrever(l);
        l.alterar(categoriaDaLoja(loja, dados.categoriaId()), dados.data(), vazioParaNull(dados.descricao()),
                dados.valor(), dados.recorrente());
        auditoria.registrar(loja, Acao.ALTEROU, "lancamento", id, antes, descrever(l));
        return LancamentoDto.de(l);
    }

    @Transactional
    public void excluir(Loja loja, Long id) {
        Lancamento l = lancamentoDaLoja(loja, id);
        auditoria.registrar(loja, Acao.EXCLUIU, "lancamento", id, descrever(l), null);
        if (l.getCopiadoDe() != null) {
            l.getCopiadoDe().pararRecorrencia(); // senão a cópia excluída voltaria na próxima abertura do mês
        }
        lancamentos.soltarCopias(l);
        lancamentos.delete(l);
    }

    /**
     * Copia os custos fixos recorrentes para os meses seguintes até o mês pedido (nunca para o futuro).
     * Cada lançamento é copiado uma vez só (copiado_de_id é único), então abrir a tela várias vezes não duplica.
     * shortcut: duas aberturas no mesmo instante podem colidir no único e uma responde erro; basta recarregar.
     */
    void garantirRecorrentes(Loja loja, YearMonth mes) {
        YearMonth ate = mes.isAfter(YearMonth.now(clock)) ? YearMonth.now(clock) : mes;
        List<Lancamento> pendentes;
        while (!(pendentes = lancamentos.recorrentesSemCopia(loja.getId(), ate.atDay(1))).isEmpty()) {
            for (Lancamento l : pendentes) {
                Lancamento copia = lancamentos.save(l.copiaParaProximoMes(clock.instant()));
                auditoria.registrar(loja, Acao.COPIOU, "lancamento", copia.getId(), "recorrente #" + l.getId(), descrever(copia));
            }
            lancamentos.flush();
        }
    }

    /* ---------- receitas de atendimento e resumo ---------- */

    @Transactional(readOnly = true)
    public List<Agendamento> atendimentos(Loja loja, YearMonth mes) {
        return agenda.concluidosEntre(loja.getId(), mes.atDay(1), mes.atEndOfMonth());
    }

    @Transactional
    public Resumo resumo(Loja loja, YearMonth mes) {
        garantirRecorrentes(loja, mes);
        YearMonth primeiro = mes.minusMonths(5);
        Map<YearMonth, List<Lancamento>> lancs = lancamentos.entre(loja.getId(), primeiro.atDay(1), mes.atEndOfMonth())
                .stream().collect(Collectors.groupingBy(l -> YearMonth.from(l.getData())));
        Map<YearMonth, List<Agendamento>> atends = agenda.concluidosEntre(loja.getId(), primeiro.atDay(1), mes.atEndOfMonth())
                .stream().collect(Collectors.groupingBy(a -> YearMonth.from(a.getData())));

        List<ResumoMes> meses = new ArrayList<>();
        for (YearMonth m = primeiro; !m.isAfter(mes); m = m.plusMonths(1)) {
            meses.add(calcular(m, lancs.getOrDefault(m, List.of()), atends.getOrDefault(m, List.of())));
        }
        return new Resumo(meses.get(5), meses.get(4), meses);
    }

    static ResumoMes calcular(YearMonth mes, List<Lancamento> lancs, List<Agendamento> atends) {
        Function<TipoLancamento, BigDecimal> soma = tipo -> lancs.stream()
                .filter(l -> l.getTipo() == tipo).map(Lancamento::getValor).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal fixo = soma.apply(TipoLancamento.FIXO);
        BigDecimal variavel = soma.apply(TipoLancamento.VARIAVEL);
        BigDecimal eventual = soma.apply(TipoLancamento.EVENTUAL);
        BigDecimal avulsa = soma.apply(TipoLancamento.RECEITA);
        BigDecimal deAtendimentos = atends.stream().map(Agendamento::getValor)
                .filter(v -> v != null).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal operacionais = fixo.add(variavel); // eventual fica fora do custo por atendimento
        BigDecimal custos = operacionais.add(eventual);
        BigDecimal receita = deAtendimentos.add(avulsa);
        BigDecimal medio = atends.isEmpty() ? null
                : operacionais.divide(BigDecimal.valueOf(atends.size()), 2, RoundingMode.HALF_UP);
        return new ResumoMes(mes.toString(), duas(deAtendimentos), duas(avulsa), duas(receita), duas(fixo), duas(variavel),
                duas(eventual), duas(custos), duas(receita.subtract(custos)), atends.size(), medio);
    }

    private static BigDecimal duas(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }

    /* ---------- preços dos serviços ---------- */

    @Transactional(readOnly = true)
    public List<PrecoDto> precos(Loja loja) {
        Map<TipoServico, BigDecimal> tabela = precos.findByLojaId(loja.getId()).stream()
                .collect(Collectors.toMap(PrecoServico::getTipoServico, PrecoServico::getValor));
        return Arrays.stream(TipoServico.values()).map(t -> new PrecoDto(t, tabela.get(t))).toList();
    }

    @Transactional
    public PrecoDto definirPreco(Loja loja, TipoServico tipo, BigDecimal valor) {
        Optional<PrecoServico> existente = precos.findByLojaIdAndTipoServico(loja.getId(), tipo);
        BigDecimal antes = existente.map(PrecoServico::getValor).orElse(null);
        if (antes != null && antes.compareTo(valor) == 0) {
            return new PrecoDto(tipo, valor); // nada mudou, nada a registrar
        }
        existente.ifPresentOrElse(p -> p.setValor(valor), () -> precos.save(new PrecoServico(loja.getId(), tipo, valor)));
        auditoria.registrar(loja, Acao.ALTEROU, "preco", tipo, antes == null ? null : "R$ " + antes.toPlainString(),
                "R$ " + valor.toPlainString());
        return new PrecoDto(tipo, valor);
    }

    /** Resumo do lançamento para o histórico (só dados financeiros, nada pessoal de cliente). */
    private static String descrever(Lancamento l) {
        return "%s · %s · %s · R$ %s%s%s".formatted(l.getData(), l.getTipo(), l.getCategoria().getNome(), l.getValor().toPlainString(),
                l.getDescricao() == null ? "" : " · " + l.getDescricao(), l.isRecorrente() ? " · recorrente" : "");
    }

    @Transactional(readOnly = true)
    public List<AuditoriaService.AuditoriaDto> historico(Loja loja) {
        return auditoria.recentes(loja);
    }

    private Categoria categoriaDaLoja(Loja loja, Long id) {
        return categorias.buscarNaLoja(id, loja.getId()).orElseThrow(() -> new Erros.NaoEncontrado("Categoria não encontrada"));
    }

    private Lancamento lancamentoDaLoja(Loja loja, Long id) {
        return lancamentos.buscarNaLoja(id, loja.getId()).orElseThrow(() -> new Erros.NaoEncontrado("Lançamento não encontrado"));
    }
}
