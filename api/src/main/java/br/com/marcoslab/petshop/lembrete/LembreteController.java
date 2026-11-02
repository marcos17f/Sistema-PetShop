package br.com.marcoslab.petshop.lembrete;

import br.com.marcoslab.petshop.loja.LojaService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/lojas/{slug}/lembretes")
public class LembreteController {

    public record MarcarEnviado(
            @NotBlank @Pattern(regexp = "(VACINA|BANHO|AGENDAMENTO):\\d+:\\d{4}-\\d{2}-\\d{2}", message = "Chave inválida")
            String chave) {
    }

    private final LojaService lojas;
    private final LembreteService lembretes;

    public LembreteController(LojaService lojas, LembreteService lembretes) {
        this.lojas = lojas;
        this.lembretes = lembretes;
    }

    @GetMapping
    public List<Lembrete> doDia(@PathVariable String slug,
                                @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data) {
        return lembretes.doDia(lojas.buscar(slug), data);
    }

    @PostMapping("/enviados")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void marcarEnviado(@PathVariable String slug, @Valid @RequestBody MarcarEnviado corpo) {
        lembretes.marcarEnviado(lojas.buscar(slug), corpo.chave());
    }
}
