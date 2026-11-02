package br.com.marcoslab.petshop.loja;

import br.com.marcoslab.petshop.erro.Erros;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LojaService {

    private final LojaRepository lojas;
    private final String slugDoPainel;

    public LojaService(LojaRepository lojas, @Value("${app.painel.loja-slug}") String slugDoPainel) {
        this.lojas = lojas;
        this.slugDoPainel = slugDoPainel;
    }

    /**
     * Busca a loja da URL. O usuário do painel só enxerga a própria loja:
     * qualquer outro slug responde 404, como se não existisse.
     */
    @Transactional(readOnly = true)
    public Loja buscar(String slug) {
        if (!slugDoPainel.equals(slug)) {
            throw new Erros.NaoEncontrado("Loja não encontrada");
        }
        return lojas.findBySlug(slug).orElseThrow(() -> new Erros.NaoEncontrado("Loja não encontrada"));
    }

    /**
     * Grava agendamentos da loja um de cada vez (até o fim da transação), para dois salvamentos ao mesmo
     * tempo não passarem juntos pela conferência do intervalo entre horários.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void travarAgenda(Loja loja) {
        lojas.travar(loja.getId());
    }
}
