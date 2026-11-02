package br.com.marcoslab.petshop.cliente;

import br.com.marcoslab.petshop.agenda.AgendamentoRepository;
import br.com.marcoslab.petshop.auditoria.Auditoria.Acao;
import br.com.marcoslab.petshop.auditoria.AuditoriaService;
import br.com.marcoslab.petshop.cliente.ClienteDtos.*;
import br.com.marcoslab.petshop.erro.Erros;
import br.com.marcoslab.petshop.loja.Loja;
import br.com.marcoslab.petshop.vacina.VacinaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

import static br.com.marcoslab.petshop.cliente.ClienteDtos.vazioParaNull;

@Service
public class ClienteService {

    private final TutorRepository tutores;
    private final PetRepository pets;
    private final AgendamentoRepository agenda;
    private final VacinaRepository vacinas;
    private final AuditoriaService auditoria;
    private final Clock clock;

    public ClienteService(TutorRepository tutores, PetRepository pets, AgendamentoRepository agenda, VacinaRepository vacinas,
                          AuditoriaService auditoria, Clock clock) {
        this.tutores = tutores;
        this.pets = pets;
        this.agenda = agenda;
        this.vacinas = vacinas;
        this.auditoria = auditoria;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<TutorDto> listar(Loja loja, String busca) {
        String termo = busca == null ? "" : busca.trim().toLowerCase(Locale.ROOT);
        return tutores.buscar(loja.getId(), termo).stream().map(TutorDto::de).toList();
    }

    @Transactional(readOnly = true)
    public TutorDto buscar(Loja loja, Long id) {
        return TutorDto.de(tutorDaLoja(loja, id));
    }

    @Transactional
    public TutorDto criar(Loja loja, NovoTutor dados) {
        Tutor t = tutores.save(new Tutor(loja, dados.nome(), dados.whatsapp(), vazioParaNull(dados.endereco()),
                vazioParaNull(dados.cpf()), vazioParaNull(dados.origem()), clock.instant()));
        return TutorDto.de(t);
    }

    @Transactional
    public TutorDto alterar(Loja loja, Long id, NovoTutor dados) {
        Tutor t = tutorDaLoja(loja, id);
        t.alterar(dados.nome(), dados.whatsapp(), vazioParaNull(dados.endereco()), vazioParaNull(dados.cpf()),
                vazioParaNull(dados.origem()));
        return TutorDto.de(t);
    }

    /**
     * Cadastro feito por engano (sem atendimento concluído): apaga de verdade o cliente, os pets, as vacinas e os agendamentos.
     * Se já tem atendimento concluído, apaga os dados pessoais (LGPD), cancela os agendamentos futuros e mantém os valores
     * recebidos no financeiro como "Cliente removido". O histórico registra a exclusão sem dado pessoal.
     */
    @Transactional
    public void excluir(Loja loja, Long id) {
        Tutor t = tutorDaLoja(loja, id);
        int qtdPets = t.getPets().size();
        if (agenda.tutorTemConcluido(id)) {
            agenda.cancelarFuturosDoTutor(id, LocalDate.now(clock));
            tutores.findById(id).orElseThrow().anonimizar(clock.instant()); // recarrega: as consultas acima limpam o contexto
            auditoria.registrar(loja, Acao.EXCLUIU, "cliente", id, "cliente #" + id + " · " + qtdPets + " pet(s)",
                    "dados pessoais apagados; atendimentos concluídos mantidos no financeiro");
        } else {
            agenda.apagarDoTutor(id);
            vacinas.apagarDoTutor(id);
            pets.apagarDoTutor(id);
            tutores.deleteById(id);
            auditoria.registrar(loja, Acao.EXCLUIU, "cliente", id, "cliente #" + id + " · " + qtdPets + " pet(s)",
                    "apagado por completo (sem atendimento concluído)");
        }
    }

    @Transactional
    public PetDto criarPet(Loja loja, Long tutorId, NovoPet dados) {
        Tutor tutor = tutorDaLoja(loja, tutorId);
        Pet p = pets.save(new Pet(tutor, dados.nome(), dados.especie(), vazioParaNull(dados.raca()), dados.pesoKg(),
                vazioParaNull(dados.observacoes()), dados.intervaloBanhoDias()));
        return PetDto.de(p);
    }

    /** Usado pelos outros módulos (vacinas, agenda) para garantir que o pet é desta loja. */
    @Transactional(readOnly = true)
    public Pet petDaLoja(Loja loja, Long petId) {
        return pets.buscarNaLoja(petId, loja.getId()).orElseThrow(() -> new Erros.NaoEncontrado("Pet não encontrado"));
    }

    private Tutor tutorDaLoja(Loja loja, Long id) {
        return tutores.findByIdAndLojaIdAndRemovidoEmIsNull(id, loja.getId())
                .orElseThrow(() -> new Erros.NaoEncontrado("Cliente não encontrado"));
    }
}
