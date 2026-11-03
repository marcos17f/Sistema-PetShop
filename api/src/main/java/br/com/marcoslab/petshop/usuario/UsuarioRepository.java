package br.com.marcoslab.petshop.usuario;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByLogin(String login);

    boolean existsByLogin(String login);

    List<Usuario> findByLojaIdOrderByNome(Long lojaId);

    Optional<Usuario> findByIdAndLojaId(Long id, Long lojaId);
}
