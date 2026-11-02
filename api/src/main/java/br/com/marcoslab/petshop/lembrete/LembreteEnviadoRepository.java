package br.com.marcoslab.petshop.lembrete;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Set;

public interface LembreteEnviadoRepository extends JpaRepository<LembreteEnviado, Long> {

    boolean existsByLojaIdAndChave(Long lojaId, String chave);

    @Query("select e.chave from LembreteEnviado e where e.loja.id = :lojaId and e.chave in :chaves")
    Set<String> jaEnviadas(@Param("lojaId") Long lojaId, @Param("chaves") Collection<String> chaves);
}
