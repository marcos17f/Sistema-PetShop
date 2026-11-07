package br.com.marcoslab.petshop.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Nenhuma requisição pode mandar mais que {@code app.seguranca.max-corpo-kb} (padrão 1 MB): o sistema só troca JSON
 * pequeno, e um corpo gigante (ex.: no webhook ou no agendamento público, que não têm login) poderia esgotar a memória.
 * Com Content-Length declarado, recusa antes de ler (413); sem ele (envio em partes), para de ler ao passar do limite.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class LimiteDeCorpo extends OncePerRequestFilter {

    private final long maxBytes;

    public LimiteDeCorpo(@Value("${app.seguranca.max-corpo-kb:1024}") long maxKb) {
        this.maxBytes = maxKb * 1024;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        if (req.getContentLengthLong() > maxBytes) {
            res.setStatus(HttpStatus.PAYLOAD_TOO_LARGE.value());
            res.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            res.setCharacterEncoding("UTF-8");
            res.getWriter().write("{\"status\":413,\"detail\":\"Requisição grande demais.\"}");
            return;
        }
        chain.doFilter(req.getContentLengthLong() < 0 ? new Limitado(req, maxBytes) : req, res);
    }

    /** Corpo sem tamanho declarado: conta o que lê e falha ao passar do limite (vira 400 na leitura do JSON). */
    private static final class Limitado extends HttpServletRequestWrapper {
        private final long max;
        private ServletInputStream entrada;

        Limitado(HttpServletRequest req, long max) {
            super(req);
            this.max = max;
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            if (entrada == null) entrada = new Contador(super.getInputStream(), max);
            return entrada;
        }
    }

    private static final class Contador extends ServletInputStream {
        private final ServletInputStream origem;
        private final long max;
        private long lidos;

        Contador(ServletInputStream origem, long max) {
            this.origem = origem;
            this.max = max;
        }

        @Override
        public int read() throws IOException {
            int b = origem.read();
            if (b >= 0) contar(1);
            return b;
        }

        @Override
        public int read(byte[] buf, int ini, int tam) throws IOException {
            int n = origem.read(buf, ini, tam);
            if (n > 0) contar(n);
            return n;
        }

        private void contar(int n) throws IOException {
            lidos += n;
            if (lidos > max) throw new IOException("Requisição grande demais");
        }

        @Override
        public boolean isFinished() {
            return origem.isFinished();
        }

        @Override
        public boolean isReady() {
            return origem.isReady();
        }

        @Override
        public void setReadListener(ReadListener l) {
            origem.setReadListener(l);
        }
    }
}
