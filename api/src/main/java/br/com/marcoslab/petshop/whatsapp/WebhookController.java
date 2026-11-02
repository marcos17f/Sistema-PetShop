package br.com.marcoslab.petshop.whatsapp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Webhook da WhatsApp Cloud API (Meta): recebe as mensagens que chegam no número da loja.
 * Segurança: só aceita o que vier assinado pela Meta (X-Hub-Signature-256 com o segredo do app), comparando em tempo
 * constante; sem segredo configurado, recusa tudo. O log não leva telefone nem texto.
 */
@RestController
@RequestMapping("/api/publico/whatsapp/webhook")
public class WebhookController {

    private static final Logger log = LoggerFactory.getLogger(WebhookController.class);

    private final MensagemRecebidaService recebidas;
    private final ObjectMapper json;
    private final String appSecret;
    private final String verifyToken;

    public WebhookController(MensagemRecebidaService recebidas, ObjectMapper json,
                             @Value("${app.whatsapp.app-secret:}") String appSecret,
                             @Value("${app.whatsapp.verify-token:}") String verifyToken) {
        this.recebidas = recebidas;
        this.json = json;
        this.appSecret = appSecret;
        this.verifyToken = verifyToken;
    }

    /** A Meta chama uma vez ao cadastrar o webhook, para conferir o token de verificação. */
    @GetMapping(produces = "text/plain")
    public ResponseEntity<String> verificar(@RequestParam("hub.mode") String modo, @RequestParam("hub.verify_token") String token,
                                            @RequestParam("hub.challenge") String desafio) {
        boolean ok = "subscribe".equals(modo) && !verifyToken.isBlank()
                && MessageDigest.isEqual(verifyToken.getBytes(StandardCharsets.UTF_8), token.getBytes(StandardCharsets.UTF_8));
        return ok ? ResponseEntity.ok(desafio) : ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    @PostMapping
    public ResponseEntity<Void> receber(@RequestBody byte[] corpo,
                                        @RequestHeader(value = "X-Hub-Signature-256", required = false) String assinatura) {
        if (!assinaturaValida(corpo, assinatura)) {
            log.warn("Webhook do WhatsApp recusado: assinatura inválida");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        try {
            for (JsonNode entrada : json.readTree(corpo).path("entry")) {
                for (JsonNode mudanca : entrada.path("changes")) {
                    JsonNode valor = mudanca.path("value");
                    String nome = valor.path("contacts").path(0).path("profile").path("name").asText("");
                    for (JsonNode m : valor.path("messages")) {
                        recebidas.recebida(m.path("from").asText(""), nome, m.path("text").path("body").asText(""));
                    }
                }
            }
        } catch (IOException e) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok().build(); // responder rápido: a Meta reenvia se demorar ou falhar
    }

    private boolean assinaturaValida(byte[] corpo, String assinatura) {
        if (appSecret.isBlank() || assinatura == null || !assinatura.startsWith("sha256=")) return false;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(appSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] esperado = HexFormat.of().formatHex(mac.doFinal(corpo)).getBytes(StandardCharsets.US_ASCII);
            return MessageDigest.isEqual(esperado, assinatura.substring(7).getBytes(StandardCharsets.US_ASCII));
        } catch (GeneralSecurityException e) {
            return false;
        }
    }
}
