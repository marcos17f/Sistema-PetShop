package br.com.marcoslab.petshop.campanha;

import br.com.marcoslab.petshop.loja.LojaService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
public class CampanhaController {

    public record NovaCampanha(
            @NotBlank(message = "Dê um título") @Size(max = 80) String titulo,
            @NotBlank(message = "Escreva a mensagem") @Size(max = 500, message = "Use no máximo 500 caracteres") String texto,
            @NotBlank(message = "Informe o modelo aprovado na Meta")
            @Pattern(regexp = "[a-z0-9_]{1,80}", message = "Nome do modelo: só letras minúsculas, números e _") String template) {
    }

    public record Descadastro(String token) {
    }

    private final LojaService lojas;
    private final CampanhaService campanhas;

    public CampanhaController(LojaService lojas, CampanhaService campanhas) {
        this.lojas = lojas;
        this.campanhas = campanhas;
    }

    @GetMapping("/api/lojas/{slug}/campanhas")
    public List<CampanhaService.Relatorio> listar(@PathVariable String slug) {
        return campanhas.listar(lojas.buscar(slug));
    }

    /** Quantos clientes vão receber (só quem aceitou promoções). */
    @GetMapping("/api/lojas/{slug}/campanhas/publico")
    public Map<String, Integer> publico(@PathVariable String slug) {
        return Map.of("clientes", campanhas.publico(lojas.buscar(slug)));
    }

    @PostMapping("/api/lojas/{slug}/campanhas")
    @ResponseStatus(HttpStatus.CREATED)
    public CampanhaService.Relatorio criar(@PathVariable String slug, @Valid @RequestBody NovaCampanha c) {
        return campanhas.criar(lojas.buscar(slug), c.titulo(), c.texto(), c.template());
    }

    /** Público (link na mensagem): sai da lista de promoções. */
    @PostMapping("/api/publico/descadastro")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void descadastrar(@RequestBody Descadastro d) {
        campanhas.descadastrar(d.token());
    }
}
