package br.com.marcoslab.petshop.online;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class LimitePorEnderecoTest {

    @Test
    void bloqueiaDepoisDoMaximoNaJanela() {
        LimitePorEndereco l = new LimitePorEndereco(2, Duration.ofHours(1), Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
        assertThat(l.permitir("1.1.1.1")).isTrue();
        assertThat(l.permitir("1.1.1.1")).isTrue();
        assertThat(l.permitir("1.1.1.1")).isFalse();
        assertThat(l.permitir("2.2.2.2")).isTrue();
    }

    @Test
    void memoriaNaoCresceSemLimiteComMilharesDeIps() {
        LimitePorEndereco l = new LimitePorEndereco(5, Duration.ofHours(1), Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
        for (int i = 0; i < 3 * LimitePorEndereco.TETO; i++) l.permitir("ip-" + i);
        assertThat(l.enderecosGuardados()).isLessThanOrEqualTo(LimitePorEndereco.TETO + 1);
    }
}
