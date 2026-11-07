package br.com.marcoslab.petshop.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Contra tentativa de adivinhar a senha: depois de {@code max-tentativas} senhas erradas do mesmo endereço (IP)
 * dentro de {@code bloqueio-minutos}, esse endereço fica bloqueado (429) por {@code bloqueio-minutos}, mesmo com a senha certa.
 * Login certo zera a contagem. O log registra o bloqueio só com o IP (nunca usuário ou senha).
 * shortcut: contagem em memória de uma instância (zera ao reiniciar); com várias instâncias da API, mover para o banco/Redis.
 * Atrás de proxy, o IP do cliente vem do X-Forwarded-For só quando o proxy é interno (server.forward-headers-strategy).
 * Memória limitada: IPs antigos são apagados e os mapas nunca passam de {@link #TETO} (ataque com milhares de IPs).
 */
@Component
public class LimiteDeLogin extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(LimiteDeLogin.class);

    private final int maxTentativas;
    private final Duration janela;
    private final Clock clock;
    static final int LIMPAR_ACIMA = 10_000;
    static final int TETO = 50_000;

    private final Map<String, Deque<Instant>> falhas = new ConcurrentHashMap<>();
    private final Map<String, Instant> bloqueadoAte = new ConcurrentHashMap<>();
    private volatile Instant proximaLimpeza = Instant.MIN;
    private final Deque<Instant> bloqueios = new java.util.concurrent.ConcurrentLinkedDeque<>();

    public LimiteDeLogin(@Value("${app.seguranca.max-tentativas:10}") int maxTentativas,
                         @Value("${app.seguranca.bloqueio-minutos:15}") int bloqueioMinutos, Clock clock) {
        this.maxTentativas = maxTentativas;
        this.janela = Duration.ofMinutes(bloqueioMinutos);
        this.clock = clock;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        Instant ate = bloqueadoAte.get(req.getRemoteAddr());
        if (ate != null && clock.instant().isBefore(ate)) {
            res.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            res.setHeader("Retry-After", String.valueOf(Duration.between(clock.instant(), ate).toSeconds()));
            res.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            res.setCharacterEncoding("UTF-8");
            res.getWriter().write("{\"status\":429,\"detail\":\"Muitas tentativas de login erradas. Tente de novo em alguns minutos.\"}");
            return;
        }
        chain.doFilter(req, res);
    }

    @EventListener
    public void falhou(AbstractAuthenticationFailureEvent e) {
        String ip = ip(e.getAuthentication().getDetails());
        if (ip == null) return;
        Instant agora = clock.instant();
        // limpeza no máximo 1x por minuto (sob ataque não varre o mapa a cada pedido); acima do teto, na hora
        int guardados = falhas.size() + bloqueadoAte.size();
        if (guardados > TETO || (guardados > LIMPAR_ACIMA && !agora.isBefore(proximaLimpeza))) limpar(agora);
        Deque<Instant> lista = falhas.computeIfAbsent(ip, k -> new ArrayDeque<>());
        synchronized (lista) {
            lista.addLast(agora);
            while (!lista.isEmpty() && lista.peekFirst().isBefore(agora.minus(janela))) lista.removeFirst();
            if (lista.size() >= maxTentativas) {
                bloqueadoAte.put(ip, agora.plus(janela));
                bloqueios.addLast(agora);
                lista.clear();
                log.warn("Login bloqueado por {} min após {} senhas erradas do IP {}", janela.toMinutes(), maxTentativas, ip);
            }
        }
    }

    @EventListener
    public void entrou(AuthenticationSuccessEvent e) {
        String ip = ip(e.getAuthentication().getDetails());
        if (ip != null) falhas.remove(ip);
    }

    int ipsGuardados() {
        return falhas.size() + bloqueadoAte.size();
    }

    private void limpar(Instant agora) {
        proximaLimpeza = agora.plusSeconds(60);
        Instant corte = agora.minus(janela);
        bloqueadoAte.values().removeIf(ate -> !agora.isBefore(ate));
        falhas.entrySet().removeIf(e -> {
            synchronized (e.getValue()) {
                return e.getValue().isEmpty() || e.getValue().peekLast().isBefore(corte);
            }
        });
        // os bloqueios ativos ficam: são eles que seguram quem está tentando adivinhar a senha
        if (falhas.size() > TETO) falhas.clear();
        if (bloqueadoAte.size() > TETO) bloqueadoAte.clear();
    }

    /** Quantos bloqueios por senha errada aconteceram nas últimas 24 h (para os avisos do sistema). */
    public int bloqueiosUltimas24h() {
        Instant limite = clock.instant().minus(Duration.ofHours(24));
        while (!bloqueios.isEmpty() && bloqueios.peekFirst().isBefore(limite)) bloqueios.pollFirst();
        return bloqueios.size();
    }

    private static String ip(Object detalhes) {
        return detalhes instanceof WebAuthenticationDetails w ? w.getRemoteAddress() : null;
    }
}
