package br.com.marcoslab.petshop.auditoria;

import br.com.marcoslab.petshop.auditoria.Auditoria.Acao;
import br.com.marcoslab.petshop.loja.Loja;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Registra quem mudou o quê no financeiro. Roda na mesma transação da alteração:
 * se a alteração for desfeita, o registro também é; se o registro falhar, a alteração não acontece.
 */
@Service
public class AuditoriaService {

    public record AuditoriaDto(Instant em, String usuario, Acao acao, String entidade, String entidadeId,
                               String antes, String depois) {
    }

    private static final int LIMITE = 1000;

    private final AuditoriaRepository auditoria;
    private final Clock clock;

    public AuditoriaService(AuditoriaRepository auditoria, Clock clock) {
        this.auditoria = auditoria;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void registrar(Loja loja, Acao acao, String entidade, Object id, String antes, String depois) {
        auditoria.save(new Auditoria(loja, usuarioAtual(), acao, entidade, String.valueOf(id), cortar(antes), cortar(depois),
                clock.instant()));
    }

    /** As 100 alterações mais recentes, da mais nova para a mais antiga. */
    @Transactional(readOnly = true)
    public List<AuditoriaDto> recentes(Loja loja) {
        return auditoria.findTop100ByLojaIdOrderByEmDescIdDesc(loja.getId()).stream()
                .map(a -> new AuditoriaDto(a.getEm(), a.getUsuario(), a.getAcao(), a.getEntidade(), a.getEntidadeId(),
                        a.getAntes(), a.getDepois()))
                .toList();
    }

    private static String usuarioAtual() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth == null ? "sistema" : auth.getName();
    }

    private static String cortar(String s) {
        return s == null || s.length() <= LIMITE ? s : s.substring(0, LIMITE - 1) + "…";
    }
}
