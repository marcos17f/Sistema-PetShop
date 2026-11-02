package br.com.marcoslab.petshop.financeiro;

import br.com.marcoslab.petshop.agenda.TipoServico;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PrecoServicoRepository extends JpaRepository<PrecoServico, PrecoServico.Chave> {

    List<PrecoServico> findByLojaId(Long lojaId);

    Optional<PrecoServico> findByLojaIdAndTipoServico(Long lojaId, TipoServico tipoServico);
}
