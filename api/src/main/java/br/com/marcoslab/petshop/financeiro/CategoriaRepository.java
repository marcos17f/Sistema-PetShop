package br.com.marcoslab.petshop.financeiro;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CategoriaRepository extends JpaRepository<Categoria, Long> {

    @Query("select c from Categoria c where c.loja.id = :lojaId order by c.tipo, c.nome")
    List<Categoria> daLoja(@Param("lojaId") Long lojaId);

    @Query("select c from Categoria c where c.id = :id and c.loja.id = :lojaId")
    Optional<Categoria> buscarNaLoja(@Param("id") Long id, @Param("lojaId") Long lojaId);

    @Query("""
            select count(c) > 0 from Categoria c
            where c.loja.id = :lojaId and c.tipo = :tipo and lower(c.nome) = lower(:nome)
            """)
    boolean existe(@Param("lojaId") Long lojaId, @Param("tipo") TipoLancamento tipo, @Param("nome") String nome);
}
