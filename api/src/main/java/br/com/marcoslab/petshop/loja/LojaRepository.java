package br.com.marcoslab.petshop.loja;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LojaRepository extends JpaRepository<Loja, Long> {
    Optional<Loja> findBySlug(String slug);
}
