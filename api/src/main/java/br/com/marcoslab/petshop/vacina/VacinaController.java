package br.com.marcoslab.petshop.vacina;

import br.com.marcoslab.petshop.cliente.ClienteService;
import br.com.marcoslab.petshop.cliente.Pet;
import br.com.marcoslab.petshop.loja.LojaService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/lojas/{slug}/pets/{petId}/vacinas")
public class VacinaController {

    public record NovaVacina(
            @NotBlank(message = "Informe a vacina") @Size(max = 80) String vacina,
            @Size(max = 40) String dose,
            @NotNull(message = "Informe a data") @PastOrPresent(message = "A data não pode ser futura") LocalDate aplicadaEm,
            LocalDate proximaEm) {

        @AssertTrue(message = "O reforço deve ser depois da aplicação")
        public boolean isReforcoDepois() {
            return proximaEm == null || aplicadaEm == null || proximaEm.isAfter(aplicadaEm);
        }
    }

    public record VacinaDto(Long id, Long petId, String vacina, String dose, LocalDate aplicadaEm, LocalDate proximaEm) {
        static VacinaDto de(AplicacaoVacina v) {
            return new VacinaDto(v.getId(), v.getPet().getId(), v.getVacina(), v.getDose() == null ? "" : v.getDose(),
                    v.getAplicadaEm(), v.getProximaEm());
        }
    }

    private final LojaService lojas;
    private final ClienteService clientes;
    private final VacinaRepository vacinas;

    public VacinaController(LojaService lojas, ClienteService clientes, VacinaRepository vacinas) {
        this.lojas = lojas;
        this.clientes = clientes;
        this.vacinas = vacinas;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<VacinaDto> listar(@PathVariable String slug, @PathVariable Long petId) {
        Pet pet = clientes.petDaLoja(lojas.buscar(slug), petId);
        return vacinas.findByPetIdOrderByAplicadaEmDesc(pet.getId()).stream().map(VacinaDto::de).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public VacinaDto registrar(@PathVariable String slug, @PathVariable Long petId, @Valid @RequestBody NovaVacina dados) {
        Pet pet = clientes.petDaLoja(lojas.buscar(slug), petId);
        String dose = dados.dose() == null || dados.dose().isBlank() ? null : dados.dose().trim();
        return VacinaDto.de(vacinas.save(new AplicacaoVacina(pet, dados.vacina(), dose, dados.aplicadaEm(), dados.proximaEm())));
    }
}
