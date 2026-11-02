package br.com.marcoslab.petshop.vacina;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface VacinaRepository extends JpaRepository<AplicacaoVacina, Long> {

    List<AplicacaoVacina> findByPetIdOrderByAplicadaEmDesc(Long petId);

    @Query("""
            select v from AplicacaoVacina v join fetch v.pet p join fetch p.tutor t
            where t.loja.id = :lojaId and v.proximaEm = :data
            """)
    List<AplicacaoVacina> vencendoEm(@Param("lojaId") Long lojaId, @Param("data") LocalDate data);
}
