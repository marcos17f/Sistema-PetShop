package br.com.marcoslab.petshop.cliente;

import br.com.marcoslab.petshop.cliente.ClienteDtos.*;
import br.com.marcoslab.petshop.erro.Erros;
import br.com.marcoslab.petshop.loja.Loja;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Locale;

import static br.com.marcoslab.petshop.cliente.ClienteDtos.vazioParaNull;

@Service
public class ClienteService {

    private final TutorRepository tutores;
    private final PetRepository pets;
    private final Clock clock;

    public ClienteService(TutorRepository tutores, PetRepository pets, Clock clock) {
        this.tutores = tutores;
        this.pets = pets;
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
        return tutores.findByIdAndLojaId(id, loja.getId()).orElseThrow(() -> new Erros.NaoEncontrado("Cliente não encontrado"));
    }
}
