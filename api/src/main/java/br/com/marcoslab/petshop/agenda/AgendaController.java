package br.com.marcoslab.petshop.agenda;

import br.com.marcoslab.petshop.auditoria.Auditoria.Acao;
import br.com.marcoslab.petshop.auditoria.AuditoriaService;
import br.com.marcoslab.petshop.cliente.ClienteService;
import br.com.marcoslab.petshop.cliente.Pet;
import br.com.marcoslab.petshop.erro.Erros;
import br.com.marcoslab.petshop.financeiro.PrecoServico;
import br.com.marcoslab.petshop.financeiro.PrecoServicoRepository;
import br.com.marcoslab.petshop.loja.Loja;
import br.com.marcoslab.petshop.loja.LojaService;
import br.com.marcoslab.petshop.whatsapp.AvaliacaoService;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static br.com.marcoslab.petshop.cliente.ClienteDtos.vazioParaNull;

@RestController
@RequestMapping("/api/lojas/{slug}/agenda")
public class AgendaController {

    public record NovoAgendamento(
            @NotNull(message = "Escolha o pet") Long petId,
            @NotNull(message = "Informe a data") LocalDate data,
            @NotNull(message = "Informe a hora") @JsonFormat(pattern = "HH:mm") LocalTime hora,
            @NotNull(message = "Escolha o serviço") TipoServico tipo,
            @Size(max = 200) String descricao,
            Boolean buscaMoto,
            @Size(max = 200) String enderecoBusca) {

        @AssertTrue(message = "Informe o endereço para a busca de moto")
        public boolean isEnderecoDaBusca() {
            return !Boolean.TRUE.equals(buscaMoto) || (enderecoBusca != null && !enderecoBusca.isBlank());
        }

        boolean moto() {
            return Boolean.TRUE.equals(buscaMoto);
        }
    }

    /** valor é opcional: ao concluir sem valor, usa o preço padrão do serviço. */
    public record NovoStatus(@NotNull StatusAgendamento status,
                             @DecimalMin(value = "0.00", message = "Valor inválido")
                             @Digits(integer = 8, fraction = 2, message = "Valor inválido") BigDecimal valor) {
    }

    public record AgendamentoDto(Long id, Long petId, String petNome, String tutorNome, LocalDate data,
                                 @JsonFormat(pattern = "HH:mm") LocalTime hora, TipoServico tipo, String descricao,
                                 StatusAgendamento status, BigDecimal valor, Canal canal, boolean buscaMoto,
                                 String enderecoBusca) {
        public static AgendamentoDto de(Agendamento a) {
            Pet p = a.getPet();
            return new AgendamentoDto(a.getId(), p.getId(), p.getNome(), p.getTutor().getNome(), a.getData(), a.getHora(),
                    a.getTipo(), a.getDescricao() == null ? "" : a.getDescricao(), a.getStatus(), a.getValor(), a.getCanal(),
                    a.isBuscaMoto(), a.getEnderecoBusca() == null ? "" : a.getEnderecoBusca());
        }
    }

    private final LojaService lojas;
    private final ClienteService clientes;
    private final AgendamentoRepository agenda;
    private final PrecoServicoRepository precos;
    private final AuditoriaService auditoria;
    private final AgendaService regras;
    private final AvaliacaoService avaliacao;

    public AgendaController(LojaService lojas, ClienteService clientes, AgendamentoRepository agenda,
                            PrecoServicoRepository precos, AuditoriaService auditoria, AgendaService regras,
                            AvaliacaoService avaliacao) {
        this.lojas = lojas;
        this.avaliacao = avaliacao;
        this.regras = regras;
        this.auditoria = auditoria;
        this.precos = precos;
        this.clientes = clientes;
        this.agenda = agenda;
    }

    public record Historico(java.math.BigDecimal totalGasto, long atendimentosConcluidos, LocalDate ultimoAtendimento,
                            List<AgendamentoDto> atendimentos) {
    }

    /** Ficha do cliente: últimos 200 atendimentos de todos os pets e quanto ele já gastou (só concluídos). */
    @GetMapping("/cliente/{tutorId}")
    @Transactional(readOnly = true)
    public Historico historicoDoCliente(@PathVariable String slug, @PathVariable Long tutorId) {
        Loja loja = lojas.buscar(slug);
        clientes.buscar(loja, tutorId); // 404 se não for cliente desta loja
        List<Agendamento> lista = agenda.doTutor(tutorId, loja.getId(), org.springframework.data.domain.PageRequest.of(0, 200));
        List<Agendamento> concluidos = lista.stream().filter(a -> a.getStatus() == StatusAgendamento.CONCLUIDO).toList();
        BigDecimal total = concluidos.stream().map(Agendamento::getValor).filter(v -> v != null).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new Historico(total, concluidos.size(), concluidos.isEmpty() ? null : concluidos.get(0).getData(),
                lista.stream().map(AgendamentoDto::de).toList());
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<AgendamentoDto> doDia(@PathVariable String slug,
                                      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data) {
        return agenda.doDia(lojas.buscar(slug).getId(), data).stream().map(AgendamentoDto::de).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public AgendamentoDto criar(@PathVariable String slug, @Valid @RequestBody NovoAgendamento dados) {
        Loja loja = lojas.buscar(slug);
        Pet pet = clientes.petDaLoja(loja, dados.petId());
        Agendamento a = regras.agendar(loja, pet, dados.data(), dados.hora(), dados.tipo(), vazioParaNull(dados.descricao()),
                dados.moto(), vazioParaNull(dados.enderecoBusca()), Canal.PAINEL);
        return AgendamentoDto.de(a);
    }

    /** Editar um agendamento lançado errado. Concluído (já está no financeiro) e cancelado não são editados. */
    @PutMapping("/{id}")
    @Transactional
    public AgendamentoDto alterar(@PathVariable String slug, @PathVariable Long id, @Valid @RequestBody NovoAgendamento dados) {
        Loja loja = lojas.buscar(slug);
        Agendamento a = agendamentoDaLoja(id, loja.getId());
        if (a.getStatus() == StatusAgendamento.CONCLUIDO || a.getStatus() == StatusAgendamento.CANCELADO) {
            throw new Erros.Conflito("Atendimento concluído ou cancelado não pode ser editado. Mude a situação antes, se foi engano.");
        }
        Pet pet = clientes.petDaLoja(loja, dados.petId());
        lojas.travarAgenda(loja);
        regras.garantirIntervalo(loja, dados.data(), dados.hora(), id);
        a.remarcar(pet, dados.data(), dados.hora(), dados.tipo(), vazioParaNull(dados.descricao()), dados.moto(),
                vazioParaNull(dados.enderecoBusca()));
        return AgendamentoDto.de(a);
    }

    /** Apagar um agendamento lançado por engano. Concluído não: o valor já entrou no financeiro. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void excluir(@PathVariable String slug, @PathVariable Long id) {
        Agendamento a = agendamentoDaLoja(id, lojas.buscar(slug).getId());
        if (a.getStatus() == StatusAgendamento.CONCLUIDO) {
            throw new Erros.Conflito("Atendimento concluído já entrou no financeiro e não pode ser excluído. Se foi engano, mude a situação antes.");
        }
        agenda.delete(a);
    }

    private Agendamento agendamentoDaLoja(Long id, Long lojaId) {
        return agenda.buscarNaLoja(id, lojaId).orElseThrow(() -> new Erros.NaoEncontrado("Agendamento não encontrado"));
    }

    @PatchMapping("/{id}/status")
    @Transactional
    public AgendamentoDto mudarStatus(@PathVariable String slug, @PathVariable Long id, @Valid @RequestBody NovoStatus corpo) {
        Loja loja = lojas.buscar(slug);
        Long lojaId = loja.getId();
        Agendamento a = agenda.buscarNaLoja(id, lojaId)
                .orElseThrow(() -> new Erros.NaoEncontrado("Agendamento não encontrado"));
        BigDecimal valor = corpo.valor();
        if (valor == null && a.getValor() == null && corpo.status() == StatusAgendamento.CONCLUIDO) {
            valor = precos.findByLojaIdAndTipoServico(lojaId, a.getTipo()).map(PrecoServico::getValor).orElse(null);
        }
        if (a.getStatus() == StatusAgendamento.CANCELADO && corpo.status() != StatusAgendamento.CANCELADO) {
            lojas.travarAgenda(loja); // reativar um cancelado: o horário pode ter sido ocupado nesse meio tempo
            regras.garantirIntervalo(loja, a.getData(), a.getHora(), a.getId());
        }
        BigDecimal valorAntes = a.getValor();
        StatusAgendamento statusAntes = a.getStatus();
        a.mudarStatus(corpo.status(), valor);
        if (statusAntes != StatusAgendamento.CONCLUIDO && a.getStatus() == StatusAgendamento.CONCLUIDO) {
            avaliacao.depoisDoAtendimento(loja, a);
        }
        boolean mudouValor = valorAntes == null ? a.getValor() != null : a.getValor().compareTo(valorAntes) != 0;
        boolean mudouReceita = mudouValor || (statusAntes == StatusAgendamento.CONCLUIDO) != (a.getStatus() == StatusAgendamento.CONCLUIDO);
        if (mudouReceita) { // só o que mexe na receita; sem nome de tutor (LGPD)
            auditoria.registrar(loja, Acao.ALTEROU, "agendamento", id,
                    statusAntes + " · R$ " + (valorAntes == null ? "-" : valorAntes.toPlainString()),
                    a.getStatus() + " · R$ " + (a.getValor() == null ? "-" : a.getValor().toPlainString()));
        }
        return AgendamentoDto.de(a);
    }
}
