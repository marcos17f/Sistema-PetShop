package br.com.marcoslab.petshop.whatsapp;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * Cliente da WhatsApp Cloud API OFICIAL (Meta). Só é usado quando WHATSAPP_TOKEN e WHATSAPP_PHONE_NUMBER_ID estão definidos.
 * Fora da janela de 24 h da conversa só dá para mandar modelo (template) aprovado na Meta; dentro dela, texto livre.
 * Nunca usar bibliotecas não oficiais (whatsapp-web.js, Baileys...): o número é banido.
 */
@Component
public class WhatsAppCloud {

    private final RestClient http;
    private final String token;
    private final String phoneNumberId;

    public WhatsAppCloud(RestClient.Builder builder,
                         @Value("${app.whatsapp.token:}") String token,
                         @Value("${app.whatsapp.phone-number-id:}") String phoneNumberId,
                         @Value("${app.whatsapp.api-url:https://graph.facebook.com/v23.0}") String apiUrl) {
        this.http = builder.baseUrl(apiUrl).build();
        this.token = token;
        this.phoneNumberId = phoneNumberId;
    }

    public boolean configurado() {
        return !token.isBlank() && !phoneNumberId.isBlank();
    }

    /** Mensagem de modelo (template) aprovado, com os textos das variáveis {{1}}, {{2}}... na ordem. */
    public String enviarTemplate(String para, String template, List<String> variaveis) {
        List<Map<String, String>> params = variaveis.stream().map(v -> Map.of("type", "text", "text", v)).toList();
        return postar(Map.of(
                "messaging_product", "whatsapp", "to", para, "type", "template",
                "template", Map.of("name", template, "language", Map.of("code", "pt_BR"),
                        "components", List.of(Map.of("type", "body", "parameters", params)))));
    }

    /** Texto livre: só dentro de 24 h depois da última mensagem do cliente (ex.: resposta rápida). */
    public String enviarTexto(String para, String texto) {
        return postar(Map.of("messaging_product", "whatsapp", "to", para, "type", "text",
                "text", Map.of("body", texto, "preview_url", true)));
    }

    private String postar(Map<String, Object> corpo) {
        JsonNode r = http.post().uri("/{id}/messages", phoneNumberId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(corpo)
                .retrieve()
                .body(JsonNode.class);
        return r == null ? null : r.path("messages").path(0).path("id").asText(null);
    }
}
