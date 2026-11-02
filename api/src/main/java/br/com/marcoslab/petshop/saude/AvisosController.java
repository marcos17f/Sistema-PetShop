package br.com.marcoslab.petshop.saude;

import br.com.marcoslab.petshop.loja.LojaService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class AvisosController {

    private final LojaService lojas;
    private final AvisosService avisos;

    public AvisosController(LojaService lojas, AvisosService avisos) {
        this.lojas = lojas;
        this.avisos = avisos;
    }

    /** Avisos do sistema (o que precisa de atenção e o que fazer). Só com login. */
    @GetMapping("/api/lojas/{slug}/avisos")
    public List<AvisosService.Aviso> avisos(@PathVariable String slug) {
        return avisos.avisos(lojas.buscar(slug));
    }
}
