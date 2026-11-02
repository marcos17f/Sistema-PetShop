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
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Objects;

@RestController
@RequestMapping("/api/lojas/{slug}/agenda")
public class AgendaController {

    public record NovoAgendamento(
            @NotNull(message = "Escolha o pet") Long petId,
            @NotNull(message = "Informe a data") LocalDate data,
            @NotNull(message = "Informe a hora") @JsonFormat(pattern = "HH:mm") LocalTime hora,
            @NotNull(message = "Escolha o serviço") TipoServico tipo,
            @Size(max = 200) String descricao) {
    }

    /** valor é opcional: ao concluir sem valor, usa o preço padrão do serviço. */
    public record NovoStatus(@NotNull StatusAgendamento status,
                             @DecimalMin(value = "0.00", message = "Valor inválido")
                             @Digits(integer = 8, fraction = 2, message = "Valor inválido") BigDecimal valor) {
    }

    public record AgendamentoDto(Long id, Long petId, String petNome, String tutorNome, LocalDate data,
                                 @JsonFormat(pattern = "HH:mm") LocalTime hora, TipoServico tipo, String descricao,
                                 StatusAgendamento status, BigDecimal valor) {
        public static AgendamentoDto de(Agendamento a) {
            Pet p = a.getPet();
            return new AgendamentoDto(a.getId(), p.getId(), p.getNome(), p.getTutor().getNome(), a.getData(), a.getHora(),
                    a.getTipo(), a.getDescricao() == null ? "" : a.getDescricao(), a.getStatus(), a.getValor());
        }
    }

    private final LojaService lojas;
    private final ClienteService clientes;
    private final AgendamentoRepository agenda;
    private final PrecoServicoRepository precos;
    private final AuditoriaService auditoria;
    private final Clock clock;
    private final int intervaloMinutos;

    public AgendaController(LojaService lojas, ClienteService clientes, AgendamentoRepository agenda,
                            PrecoServicoRepository precos, AuditoriaService auditoria, Clock clock,
                            @Value("${app.agenda.intervalo-minutos}") int intervaloMinutos) {
        this.lojas = lojas;
        this.intervaloMinutos = intervaloMinutos;
        this.auditoria = auditoria;
        this.precos = precos;
        this.clientes = clientes;
        this.agenda = agenda;
        this.clock = clock;
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
        lojas.travarAgenda(loja);
        garantirIntervalo(loja.getId(), dados.data(), dados.hora(), null);
        String descricao = dados.descricao() == null || dados.descricao().isBlank() ? null : dados.descricao().trim();
        Agendamento a = agenda.save(new Agendamento(loja, pet, dados.data(), dados.hora(), dados.tipo(), descricao, clock.instant()));
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
        garantirIntervalo(loja.getId(), dados.data(), dados.hora(), id);
        String descricao = dados.descricao() == null || dados.descricao().isBlank() ? null : dados.descricao().trim();
        a.remarcar(pet, dados.data(), dados.hora(), dados.tipo(), descricao);
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
            garantirIntervalo(lojaId, a.getData(), a.getHora(), a.getId());
        }
        BigDecimal valorAntes = a.getValor();
        StatusAgendamento statusAntes = a.getStatus();
        a.mudarStatus(corpo.status(), valor);
        boolean mudouValor = valorAntes == null ? a.getValor() != null : a.getValor().compareTo(valorAntes) != 0;
        boolean mudouReceita = mudouValor || (statusAntes == StatusAgendamento.CONCLUIDO) != (a.getStatus() == StatusAgendamento.CONCLUIDO);
        if (mudouReceita) { // só o que mexe na receita; sem nome de tutor (LGPD)
            auditoria.registrar(loja, Acao.ALTEROU, "agendamento", id,
                    statusAntes + " · R$ " + (valorAntes == null ? "-" : valorAntes.toPlainString()),
                    a.getStatus() + " · R$ " + (a.getValor() == null ? "-" : a.getValor().toPlainString()));
        }
        return AgendamentoDto.de(a);
    }

    /**
     * Entre um atendimento e outro precisa haver pelo menos {@code app.agenda.intervalo-minutos}
     * (de qualquer cliente). Ex.: com 40 min e um banho às 09:00, só dá para marcar até 08:20 ou a partir de 09:40.
     */
    private void garantirIntervalo(Long lojaId, LocalDate data, LocalTime hora, Long ignorar) {
        agenda.ativosDoDia(lojaId, data).stream()
                .filter(o -> !Objects.equals(o.getId(), ignorar))
                .filter(o -> Math.abs(Duration.between(o.getHora(), hora).toMinutes()) < intervaloMinutos)
                .findFirst()
                .ifPresent(o -> {
                    throw new Erros.Conflito("Muito perto do atendimento das %s. Deixe pelo menos %d minutos entre um atendimento e outro."
                            .formatted(o.getHora(), intervaloMinutos));
                });
    }
}
