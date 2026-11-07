package br.com.marcoslab.petshop.config;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.web.authentication.WebAuthenticationDetails;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class LimiteDeLoginTest {

    private final AtomicReference<Instant> agora = new AtomicReference<>(Instant.EPOCH);
    private final Clock relogio = new Clock() {
        public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(java.time.ZoneId z) { return this; }
        public Instant instant() { return agora.get(); }
    };

    private static void errou(LimiteDeLogin l, String ip) {
        var auth = new UsernamePasswordAuthenticationToken("x", "y");
        auth.setDetails(new WebAuthenticationDetails(ip, null));
        l.falhou(new AuthenticationFailureBadCredentialsEvent(auth, new BadCredentialsException("senha errada")));
    }

    @Test
    void ipsAntigosSaoApagadosEAMemoriaFicaLimitada() {
        LimiteDeLogin l = new LimiteDeLogin(10, 15, relogio);
        for (int i = 0; i <= LimiteDeLogin.LIMPAR_ACIMA; i++) errou(l, "ip-" + i);
        agora.set(Instant.EPOCH.plusSeconds(16 * 60)); // passou a janela: tudo aquilo é antigo
        errou(l, "novo");
        assertThat(l.ipsGuardados()).isEqualTo(1);

        for (int i = 0; i < 3 * LimiteDeLogin.TETO; i++) errou(l, "ataque-" + i);
        assertThat(l.ipsGuardados()).isLessThanOrEqualTo(2 * LimiteDeLogin.TETO + 2);
    }

    @Test
    void bloqueioContinuaDepoisDaLimpeza() {
        LimiteDeLogin l = new LimiteDeLogin(3, 15, relogio);
        for (int i = 0; i < 3; i++) errou(l, "atacante");
        for (int i = 0; i <= LimiteDeLogin.LIMPAR_ACIMA; i++) errou(l, "ip-" + i);
        assertThat(l.ipsGuardados()).isGreaterThan(0);
        HttpServletRequest req = org.mockito.Mockito.mock(HttpServletRequest.class);
        org.mockito.Mockito.when(req.getRemoteAddr()).thenReturn("atacante");
        var res = new org.springframework.mock.web.MockHttpServletResponse();
        try {
            l.doFilter(req, res, (a, b) -> { });
        } catch (Exception e) {
            throw new AssertionError(e);
        }
        assertThat(res.getStatus()).isEqualTo(429);
    }
}
