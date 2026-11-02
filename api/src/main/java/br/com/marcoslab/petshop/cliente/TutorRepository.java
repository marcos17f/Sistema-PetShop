package br.com.marcoslab.petshop.cliente;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TutorRepository extends JpaRepository<Tutor, Long> {

    /** Busca por nome do tutor, WhatsApp ou nome de um dos pets (igual à busca do painel). */
    @Query("""
            select distinct t from Tutor t left join t.pets p
            where t.loja.id = :lojaId
              and (:busca = ''
                   or lower(t.nome) like concat('%', :busca, '%')
                   or t.whatsapp like concat('%', :busca, '%')
                   or lower(p.nome) like concat('%', :busca, '%'))
            order by t.nome
            """)
    List<Tutor> buscar(@Param("lojaId") Long lojaId, @Param("busca") String busca);

    Optional<Tutor> findByIdAndLojaId(Long id, Long lojaId);
}
