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

    @PostMapping("/{id}/pets")
    @ResponseStatus(HttpStatus.CREATED)
    public PetDto criarPet(@PathVariable String slug, @PathVariable Long id, @Valid @RequestBody NovoPet dados) {
        return clientes.criarPet(lojas.buscar(slug), id, dados);
    }
}
