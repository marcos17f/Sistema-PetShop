package br.com.marcoslab.petshop.cliente;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PetRepository extends JpaRepository<Pet, Long> {

    @Query("select p from Pet p join fetch p.tutor t where p.id = :id and t.loja.id = :lojaId")
    Optional<Pet> buscarNaLoja(@Param("id") Long id, @Param("lojaId") Long lojaId);

    /** Pets com retorno de banho programado (o filtro da data é feito em Java, é poucos registros). */
    @Query("""
            select p from Pet p join fetch p.tutor t
            where t.loja.id = :lojaId and p.ultimoBanho is not null and p.intervaloBanhoDias is not null
            """)
    List<Pet> comRetornoDeBanho(@Param("lojaId") Long lojaId);
}
