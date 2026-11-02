package br.com.marcoslab.petshop;

import br.com.marcoslab.petshop.whatsapp.WhatsAppCloud;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Avisos do sistema: limite do plano de WhatsApp, modo simulado, segurança; e o /actuator/health para monitor externo. */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@TestPropertySource(properties = "app.limites.whatsapp-mensagens-mes=5")
class AvisosApiTest {

    private static final String BASE = "/api/lojas/petshop-cleiton";

    @Autowired
    MockMvc mvc;

    @MockitoBean
    WhatsAppCloud cloud;

    private MockHttpServletRequestBuilder logado(MockHttpServletRequestBuilder req) {
        return req.with(httpBasic("loja", "senha-teste")).contentType(MediaType.APPLICATION_JSON);
    }

    @Test
    void avisaQuandoOPlanoDeWhatsappChegaAoLimite() throws Exception {
        when(cloud.configurado()).thenReturn(true);
        when(cloud.enviarTexto(anyString(), anyString())).thenReturn("wamid.X");

        for (int i = 0; i < 4; i++) mvc.perform(logado(post(BASE + "/whatsapp/teste")));
        mvc.perform(logado(get(BASE + "/avisos")))
                .andExpect(jsonPath("$[?(@.area == 'WhatsApp')].titulo", contains("Mensagens do mês chegando ao limite")))
                .andExpect(jsonPath("$[?(@.area == 'WhatsApp')].nivel", contains("ATENCAO")));

        mvc.perform(logado(post(BASE + "/whatsapp/teste")));
        mvc.perform(logado(get(BASE + "/avisos")))
                .andExpect(jsonPath("$[0].nivel").value("CRITICO"))
                .andExpect(jsonPath("$[0].titulo").value("Limite de mensagens do mês atingido"))
                .andExpect(jsonPath("$[0].acao").value(containsString("plano maior")));
    }

    @Test
    void avisaModoSimuladoComLembretesLigadosESenhaSemCriptografia() throws Exception {
        when(cloud.configurado()).thenReturn(false);
        mvc.perform(logado(put(BASE + "/whatsapp")).content("""
                {"lembretesAutomaticos":true,"respostaRapida":false,"textoRespostaRapida":""}
                """));
        mvc.perform(logado(get(BASE + "/avisos")))
                .andExpect(jsonPath("$[*].titulo", hasItems(
                        "Lembretes automáticos ligados, mas nada está saindo",
                        "Senha do painel sem criptografia")));
        mvc.perform(get(BASE + "/avisos")).andExpect(status().isUnauthorized());
    }

    @Test
    void avisaBloqueioDeSenhaErrada() throws Exception {
        for (int i = 0; i < 10; i++) {
            mvc.perform(get(BASE + "/tutores").with(r -> { r.setRemoteAddr("10.9.9.9"); return r; }).with(httpBasic("loja", "x" + i)));
        }
        mvc.perform(logado(get(BASE + "/avisos")))
                .andExpect(jsonPath("$[?(@.area == 'Segurança')].titulo", hasItem("Tentativas de senha erradas bloqueadas")));
    }

    @Test
    void saudeParaMonitorExternoSemDetalhes() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist());
        // nada além da saúde fica exposto
        mvc.perform(get("/actuator/env")).andExpect(status().is4xxClientError());
        mvc.perform(get("/actuator/env").with(httpBasic("loja", "senha-teste"))).andExpect(status().is4xxClientError());
    }
}
