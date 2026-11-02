package br.com.marcoslab.petshop.saude;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedDeque;

/** Conta respostas com erro do servidor (5xx) para o aviso "muitos erros na última hora". Não guarda dados da requisição. */
@Component
public class ContadorDeErros extends OncePerRequestFilter {

    private final Deque<Instant> erros = new ConcurrentLinkedDeque<>();
    private final Clock clock;

    public ContadorDeErros(Clock clock) {
        this.clock = clock;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        try {
            chain.doFilter(req, res);
        } catch (IOException | ServletException | RuntimeException e) {
            erros.addLast(clock.instant());
            throw e;
        }
        if (res.getStatus() >= 500) erros.addLast(clock.instant());
    }

    public int ultimaHora() {
        Instant limite = clock.instant().minus(Duration.ofHours(1));
        while (!erros.isEmpty() && erros.peekFirst().isBefore(limite)) erros.pollFirst();
        return erros.size();
    }
}
