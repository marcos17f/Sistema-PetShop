package br.com.marcoslab.petshop.cliente;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PetRepository extends JpaRepository<Pet, Long> {

    @Query("select p from Pet p join fetch p.tutor t where p.id = :id and t.loja.id = :lojaId and t.removidoEm is null")
    Optional<Pet> buscarNaLoja(@Param("id") Long id, @Param("lojaId") Long lojaId);

    /** Pets com retorno de banho programado (o filtro da data é feito em Java, é poucos registros). */
    @Query("""
            select p from Pet p join fetch p.tutor t
            where t.loja.id = :lojaId and t.removidoEm is null and p.ultimoBanho is not null and p.intervaloBanhoDias is not null
            """)
    List<Pet> comRetornoDeBanho(@Param("lojaId") Long lojaId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from Pet p where p.tutor.id = :tutorId")
    void apagarDoTutor(@Param("tutorId") Long tutorId);
}
