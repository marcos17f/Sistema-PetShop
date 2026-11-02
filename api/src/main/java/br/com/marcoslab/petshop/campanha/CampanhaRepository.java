package br.com.marcoslab.petshop.campanha;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CampanhaRepository extends JpaRepository<Campanha, Long> {

    List<Campanha> findTop50ByLojaIdOrderByCriadaEmDescIdDesc(Long lojaId);

    List<Campanha> findByLojaIdAndStatusOrderByCriadaEm(Long lojaId, Campanha.Status status);

    Optional<Campanha> findByIdAndLojaId(Long id, Long lojaId);

    @Query("select e from EnvioCampanha e join fetch e.tutor where e.campanha.id = :id and e.status = br.com.marcoslab.petshop.campanha.EnvioCampanha.Status.PENDENTE order by e.id")
    List<EnvioCampanha> pendentes(@Param("id") Long campanhaId);

    @Query("select e.status, count(e) from EnvioCampanha e where e.campanha.id = :id group by e.status")
    List<Object[]> contagem(@Param("id") Long campanhaId);
}
