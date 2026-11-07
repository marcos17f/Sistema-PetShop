package br.com.marcoslab.petshop.online;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Limita quantas vezes o mesmo endereço (IP) usa uma rota pública dentro de uma janela.
 * shortcut: em memória de uma instância (zera ao reiniciar); com várias instâncias, mover para o banco/Redis.
 * Memória limitada: endereços sem uso recente são apagados; contra ataque com milhares de IPs, o mapa nunca passa de
 * {@link #TETO} (nesse caso zera a contagem: melhor perder o limite por um instante do que derrubar a API).
 */
class LimitePorEndereco {

    private final int maximo;
    private final Duration janela;
    private final Clock clock;
    static final int LIMPAR_ACIMA = 10_000;
    static final int TETO = 50_000;

    private final Map<String, Deque<Instant>> usos = new ConcurrentHashMap<>();
    private volatile Instant proximaLimpeza = Instant.MIN;

    LimitePorEndereco(int maximo, Duration janela, Clock clock) {
        this.maximo = maximo;
        this.janela = janela;
        this.clock = clock;
    }

    /** @return false se o endereço já usou o máximo na janela (e não conta este uso). */
    boolean permitir(String ip) {
        Instant agora = clock.instant();
        // limpeza no máximo 1x por minuto (sob ataque não varre o mapa a cada pedido); acima do teto, na hora
        if (usos.size() > TETO || (usos.size() > LIMPAR_ACIMA && !agora.isBefore(proximaLimpeza))) limpar(agora);
        Deque<Instant> lista = usos.computeIfAbsent(ip, k -> new ArrayDeque<>());
        synchronized (lista) {
            while (!lista.isEmpty() && lista.peekFirst().isBefore(agora.minus(janela))) lista.removeFirst();
            if (lista.size() >= maximo) return false;
            lista.addLast(agora);
            return true;
        }
    }

    int enderecosGuardados() {
        return usos.size();
    }

    private void limpar(Instant agora) {
        proximaLimpeza = agora.plusSeconds(60);
        Instant corte = agora.minus(janela);
        usos.entrySet().removeIf(e -> {
            synchronized (e.getValue()) {
                return e.getValue().isEmpty() || e.getValue().peekLast().isBefore(corte);
            }
        });
        if (usos.size() > TETO) usos.clear();
    }
}
