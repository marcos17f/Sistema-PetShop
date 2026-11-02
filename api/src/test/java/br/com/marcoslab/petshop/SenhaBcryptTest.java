package br.com.marcoslab.petshop;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Garante que a senha gerada pelo comando do README (htpasswd, formato $2y$) funciona em PAINEL_SENHA. */
class SenhaBcryptTest {

    @Test
    void aceitaHashGeradoPeloHtpasswd() {
        PasswordEncoder encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
        // docker run --rm httpd:2.4-alpine htpasswd -bnBC 10 "" "teste-123"
        String painelSenha = "{bcrypt}$2y$10$Cci75GCTxeO1QI923AXJa.eNVjZV8GafGpqEZeF.PhDcFu4gOtvBm";
        assertTrue(encoder.matches("teste-123", painelSenha));
        assertFalse(encoder.matches("errada", painelSenha));
    }
}
