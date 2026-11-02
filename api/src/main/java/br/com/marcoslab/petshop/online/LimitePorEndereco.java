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
 */
class LimitePorEndereco {

    private final int maximo;
    private final Duration janela;
    private final Clock clock;
    private final Map<String, Deque<Instant>> usos = new ConcurrentHashMap<>();

    LimitePorEndereco(int maximo, Duration janela, Clock clock) {
        this.maximo = maximo;
        this.janela = janela;
        this.clock = clock;
    }

    /** @return false se o endereço já usou o máximo na janela (e não conta este uso). */
    boolean permitir(String ip) {
        Instant agora = clock.instant();
        Deque<Instant> lista = usos.computeIfAbsent(ip, k -> new ArrayDeque<>());
        synchronized (lista) {
            while (!lista.isEmpty() && lista.peekFirst().isBefore(agora.minus(janela))) lista.removeFirst();
            if (lista.size() >= maximo) return false;
            lista.addLast(agora);
            return true;
        }
    }
}
