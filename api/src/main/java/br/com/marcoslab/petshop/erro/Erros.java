package br.com.marcoslab.petshop.erro;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

/** Exceções da API e como viram resposta HTTP (RFC 7807). */
@RestControllerAdvice
public class Erros {

    public static class NaoEncontrado extends RuntimeException {
        public NaoEncontrado(String mensagem) {
            super(mensagem);
        }
    }

    /** Limite de uso atingido (ex.: muitos agendamentos online do mesmo endereço). */
    public static class MuitasTentativas extends RuntimeException {
        public MuitasTentativas(String mensagem) {
            super(mensagem);
        }
    }

    public static class Conflito extends RuntimeException {
        public Conflito(String mensagem) {
            super(mensagem);
        }
    }

    @ExceptionHandler(NaoEncontrado.class)
    public ProblemDetail naoEncontrado(NaoEncontrado e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(MuitasTentativas.class)
    public ProblemDetail muitasTentativas(MuitasTentativas e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, e.getMessage());
    }

    @ExceptionHandler(Conflito.class)
    public ProblemDetail conflito(Conflito e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }

    /** Ex.: duas abas abrindo o mesmo mês ao mesmo tempo (cópia recorrente duplicada). Sem detalhes do banco na resposta. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail integridade(DataIntegrityViolationException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "Os dados mudaram enquanto você salvava. Recarregue a página e tente de novo.");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail validacao(MethodArgumentNotValidException e) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Confira os campos destacados.");
        Map<String, String> campos = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors().forEach(f -> campos.putIfAbsent(f.getField(), f.getDefaultMessage()));
        p.setProperty("campos", campos);
        return p;
    }
}
