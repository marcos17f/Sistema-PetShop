package br.com.marcoslab.petshop.vacina;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface VacinaRepository extends JpaRepository<AplicacaoVacina, Long> {

    List<AplicacaoVacina> findByPetIdOrderByAplicadaEmDesc(Long petId);

    Optional<AplicacaoVacina> findByIdAndPetId(Long id, Long petId);

    @Query("""
            select v from AplicacaoVacina v join fetch v.pet p join fetch p.tutor t
            where t.loja.id = :lojaId and t.removidoEm is null and v.proximaEm = :data
            """)
    List<AplicacaoVacina> vencendoEm(@Param("lojaId") Long lojaId, @Param("data") LocalDate data);

    /** Usado ao apagar um cliente cadastrado por engano. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from AplicacaoVacina v where v.pet.id in (select p.id from Pet p where p.tutor.id = :tutorId)")
    void apagarDoTutor(@Param("tutorId") Long tutorId);
}
