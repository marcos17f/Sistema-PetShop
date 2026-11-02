package br.com.marcoslab.petshop.financeiro;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface LancamentoRepository extends JpaRepository<Lancamento, Long> {

    @Query("""
            select l from Lancamento l join fetch l.categoria
            where l.loja.id = :lojaId and l.data between :inicio and :fim
            order by l.data, l.id
            """)
    List<Lancamento> entre(@Param("lojaId") Long lojaId, @Param("inicio") LocalDate inicio, @Param("fim") LocalDate fim);

    @Query("select l from Lancamento l join fetch l.categoria where l.id = :id and l.loja.id = :lojaId")
    Optional<Lancamento> buscarNaLoja(@Param("id") Long id, @Param("lojaId") Long lojaId);

    /** Recorrentes de antes do mês que ainda não foram copiados para o mês seguinte. */
    @Query("""
            select l from Lancamento l
            where l.loja.id = :lojaId and l.recorrente = true and l.data < :inicio
              and not exists (select c.id from Lancamento c where c.copiadoDe = l)
            """)
    List<Lancamento> recorrentesSemCopia(@Param("lojaId") Long lojaId, @Param("inicio") LocalDate inicio);

    /** Antes de excluir: as cópias perdem o vínculo e seguem como lançamentos normais. */
    @Modifying(flushAutomatically = true)
    @Query("update Lancamento c set c.copiadoDe = null where c.copiadoDe = :l")
    void soltarCopias(@Param("l") Lancamento l);

    long countByCategoriaId(Long categoriaId);
}
