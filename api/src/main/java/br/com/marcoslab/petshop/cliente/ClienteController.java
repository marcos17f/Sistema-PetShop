package br.com.marcoslab.petshop.cliente;

import br.com.marcoslab.petshop.cliente.ClienteDtos.*;
import br.com.marcoslab.petshop.loja.LojaService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/lojas/{slug}/tutores")
public class ClienteController {

    private final LojaService lojas;
    private final ClienteService clientes;

    public ClienteController(LojaService lojas, ClienteService clientes) {
        this.lojas = lojas;
        this.clientes = clientes;
    }

    @GetMapping
    public List<TutorDto> listar(@PathVariable String slug, @RequestParam(defaultValue = "") String busca) {
        return clientes.listar(lojas.buscar(slug), busca);
    }

    @GetMapping("/{id}")
    public TutorDto buscar(@PathVariable String slug, @PathVariable Long id) {
        return clientes.buscar(lojas.buscar(slug), id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TutorDto criar(@PathVariable String slug, @Valid @RequestBody NovoTutor dados) {
        return clientes.criar(lojas.buscar(slug), dados);
    }

    @PutMapping("/{id}")
    public TutorDto alterar(@PathVariable String slug, @PathVariable Long id, @Valid @RequestBody NovoTutor dados) {
        return clientes.alterar(lojas.buscar(slug), id, dados);
    }

    /** Cadastro por engano é apagado; cliente com atendimento concluído tem os dados pessoais apagados (ver ClienteService). */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(@PathVariable String slug, @PathVariable Long id) {
        clientes.excluir(lojas.buscar(slug), id);
    }

    @PostMapping("/{id}/pets")
    @ResponseStatus(HttpStatus.CREATED)
    public PetDto criarPet(@PathVariable String slug, @PathVariable Long id, @Valid @RequestBody NovoPet dados) {
        return clientes.criarPet(lojas.buscar(slug), id, dados);
    }

    @PutMapping("/{id}/pets/{petId}")
    public PetDto alterarPet(@PathVariable String slug, @PathVariable Long id, @PathVariable Long petId,
                             @Valid @RequestBody NovoPet dados) {
        return clientes.alterarPet(lojas.buscar(slug), id, petId, dados);
    }

    @DeleteMapping("/{id}/pets/{petId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluirPet(@PathVariable String slug, @PathVariable Long id, @PathVariable Long petId) {
        clientes.excluirPet(lojas.buscar(slug), id, petId);
    }
}
