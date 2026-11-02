package br.com.marcoslab.petshop.loja;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface LojaRepository extends JpaRepository<Loja, Long> {
    Optional<Loja> findBySlug(String slug);

    /** SELECT ... FOR UPDATE na loja: quem chega depois espera o outro terminar de gravar. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from Loja l where l.id = :id")
    Optional<Loja> travar(@Param("id") Long id);
}
