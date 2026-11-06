package br.com.marcoslab.petshop;

import br.com.marcoslab.petshop.whatsapp.WhatsAppCloud;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import org.springframework.test.web.client.match.MockRestRequestMatchers;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** WhatsApp: modo simulado (sem API oficial) e modo oficial (Cloud API da Meta, imitada no teste). */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class WhatsAppApiTest {

    private static final String BASE = "/api/lojas/petshop-cleiton";

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    org.springframework.jdbc.core.JdbcTemplate jdbc;

    @MockitoBean
    WhatsAppCloud cloud;

    private MockHttpServletRequestBuilder logado(MockHttpServletRequestBuilder req) {
        return req.with(httpBasic("loja", "senha-teste")).contentType(MediaType.APPLICATION_JSON);
    }

    private long criar(String caminho, String corpo) throws Exception {
        return json.readTree(mvc.perform(logado(post(BASE + caminho)).content(corpo))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asLong();
    }

    /** Pet com vacina vencendo amanhã: gera um lembrete para o envio de "amanhã". */
    private void lembreteParaAmanha() throws Exception {
        long tutor = criar("/tutores", """
                {"nome":"Ana Paula Souza","whatsapp":"(62) 99999-1234"}
                """);
        long pet = criar("/tutores/" + tutor + "/pets", """
                {"nome":"Thor","especie":"Cão"}
                """);
        LocalDate amanha = LocalDate.now(ZoneId.of("America/Sao_Paulo")).plusDays(1);
        criar("/pets/" + pet + "/vacinas", """
                {"vacina":"V10","dose":"Reforço anual","aplicadaEm":"%s","proximaEm":"%s"}
                """.formatted(amanha.minusYears(1), amanha));
    }

    @Test
    void modoSimuladoRegistraMensagensSemDuplicarLembrete() throws Exception {
        when(cloud.configurado()).thenReturn(false);
        mvc.perform(logado(get(BASE + "/whatsapp")))
                .andExpect(jsonPath("$.oficial").value(false))
                .andExpect(jsonPath("$.lembretesAutomaticos").value(false));
        mvc.perform(logado(put(BASE + "/whatsapp")).content("""
                        {"lembretesAutomaticos":true,"respostaRapida":true,"textoRespostaRapida":"Oi! Agende aqui: {link}"}
                        """))
                .andExpect(jsonPath("$.lembretesAutomaticos").value(true))
                .andExpect(jsonPath("$.textoRespostaRapida").value("Oi! Agende aqui: {link}"));

        // o número real da loja ainda não foi informado (V14 tirou o fictício)
        jdbc.update("update loja set whatsapp = '5562999990000' where slug = 'petshop-cleiton'");
        mvc.perform(logado(post(BASE + "/whatsapp/teste")))
                .andExpect(jsonPath("$.status").value("SIMULADA"))
                .andExpect(jsonPath("$.para").value("5562999990000"));

        lembreteParaAmanha();
        mvc.perform(logado(post(BASE + "/whatsapp/lembretes-agora")))
                .andExpect(jsonPath("$.simuladas").value(1))
                .andExpect(jsonPath("$.enviadas").value(0));
        // rodar de novo não manda o mesmo lembrete outra vez
        mvc.perform(logado(post(BASE + "/whatsapp/lembretes-agora")))
                .andExpect(jsonPath("$.simuladas").value(0))
                .andExpect(jsonPath("$.jaFeitas").value(1));

        mvc.perform(logado(get(BASE + "/whatsapp/mensagens")))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].tipo").value("LEMBRETE"))
                .andExpect(jsonPath("$[0].para").value("5562999991234"))
                .andExpect(jsonPath("$[0].texto").value(containsString("vacina V10")));
        // simulado não conta como enviado: continua pendente na tela Lembretes para mandar por clique
        LocalDate amanha = LocalDate.now(ZoneId.of("America/Sao_Paulo")).plusDays(1);
        mvc.perform(logado(get(BASE + "/lembretes").param("data", amanha.toString())))
                .andExpect(jsonPath("$[0].enviado").value(false));
        // só com login
        mvc.perform(get(BASE + "/whatsapp/mensagens")).andExpect(status().isUnauthorized());
    }

    @Test
    void modoOficialEnviaPeloModeloEMarcaOLembrete() throws Exception {
        when(cloud.configurado()).thenReturn(true);
        when(cloud.enviarTemplate(anyString(), anyString(), anyList())).thenReturn("wamid.TESTE");
        lembreteParaAmanha();

        mvc.perform(logado(post(BASE + "/whatsapp/lembretes-agora")))
                .andExpect(jsonPath("$.enviadas").value(1));
        org.mockito.Mockito.verify(cloud).enviarTemplate(eq("5562999991234"), eq("lembrete_pet"),
                argThat((List<String> v) -> v.get(0).equals("A Arca Pet") && v.get(1).contains("vacina V10")));

        LocalDate amanha = LocalDate.now(ZoneId.of("America/Sao_Paulo")).plusDays(1);
        mvc.perform(logado(get(BASE + "/lembretes").param("data", amanha.toString())))
                .andExpect(jsonPath("$[0].enviado").value(true));
        mvc.perform(logado(get(BASE + "/whatsapp/mensagens"))).andExpect(jsonPath("$[0].status").value("ENVIADA"));
    }

    /** O formato exato que vai para a Meta (Cloud API), com o servidor dela imitado. */
    @Test
    void formatoDoPedidoParaACloudApi() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer meta = MockRestServiceServer.bindTo(builder).build();
        WhatsAppCloud real = new WhatsAppCloud(builder, "TOKEN-TESTE", "123456", "https://graph.facebook.com/v23.0");

        meta.expect(requestTo("https://graph.facebook.com/v23.0/123456/messages"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer TOKEN-TESTE"))
                .andExpect(MockRestRequestMatchers.jsonPath("$.messaging_product").value("whatsapp"))
                .andExpect(MockRestRequestMatchers.jsonPath("$.to").value("5562999991234"))
                .andExpect(MockRestRequestMatchers.jsonPath("$.type").value("template"))
                .andExpect(MockRestRequestMatchers.jsonPath("$.template.name").value("lembrete_pet"))
                .andExpect(MockRestRequestMatchers.jsonPath("$.template.language.code").value("pt_BR"))
                .andExpect(MockRestRequestMatchers.jsonPath("$.template.components[0].parameters[1].text").value("Amanhã é dia da vacina"))
                .andRespond(withSuccess("{\"messages\":[{\"id\":\"wamid.ABC\"}]}", MediaType.APPLICATION_JSON));

        String id = real.enviarTemplate("5562999991234", "lembrete_pet", List.of("Pet Shop", "Amanhã é dia da vacina"));
        org.junit.jupiter.api.Assertions.assertEquals("wamid.ABC", id);
        meta.verify();
    }
}
