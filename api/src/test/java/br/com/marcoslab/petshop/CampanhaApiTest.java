package br.com.marcoslab.petshop;

import br.com.marcoslab.petshop.campanha.CampanhaService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Campanhas só para quem aceitou (LGPD), limite por dia, descadastro pelo link e pedido de avaliação no Google. */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@TestPropertySource(properties = {"app.campanhas.limite-dia=1", "app.site-url=https://petshop.test"})
class CampanhaApiTest {

    private static final String BASE = "/api/lojas/petshop-cleiton";

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    CampanhaService campanhas;

    private MockHttpServletRequestBuilder logado(MockHttpServletRequestBuilder req) {
        return req.with(httpBasic("loja", "senha-teste")).contentType(MediaType.APPLICATION_JSON);
    }

    private long cliente(String nome, String whatsapp) throws Exception {
        return json.readTree(mvc.perform(logado(post(BASE + "/tutores")).content("""
                {"nome":"%s","whatsapp":"%s"}
                """.formatted(nome, whatsapp))).andReturn().getResponse().getContentAsString()).get("id").asLong();
    }

    private static final String CAMPANHA = """
            {"titulo":"Banho em dobro","texto":"Nesta semana, banho com hidratação grátis!","template":"promocao_loja"}
            """;

    @Test
    void campanhaSoParaQuemAceitouComLimitePorDiaEDescadastro() throws Exception {
        long ana = cliente("Ana Souza", "(62) 99999-1111");
        long bruno = cliente("Bruno Lima", "(62) 99999-2222");
        cliente("Carla Sem Aceite", "(62) 99999-3333");

        // sem ninguém na lista: não cria
        mvc.perform(logado(post(BASE + "/campanhas")).content(CAMPANHA))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(containsString("só vai para quem aceitou")));

        for (long id : new long[]{ana, bruno}) {
            mvc.perform(logado(put(BASE + "/tutores/" + id + "/promocoes")).content("{\"aceita\":true}"))
                    .andExpect(jsonPath("$.promocoesDesde").isNotEmpty());
        }
        mvc.perform(logado(get(BASE + "/campanhas/publico"))).andExpect(jsonPath("$.clientes").value(2));

        // limite de 1 por dia: sai 1 (simulada, sem API oficial) e 1 fica para amanhã
        mvc.perform(logado(post(BASE + "/campanhas")).content(CAMPANHA))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ENVIANDO"))
                .andExpect(jsonPath("$.envios.SIMULADA").value(1))
                .andExpect(jsonPath("$.envios.PENDENTE").value(1));
        mvc.perform(logado(get(BASE + "/whatsapp/mensagens")))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].tipo").value("CAMPANHA"))
                .andExpect(jsonPath("$[0].para").value("5562999991111"))
                .andExpect(jsonPath("$[0].texto").value(matchesPattern("(?s).*hidratação grátis!.*https://petshop\\.test/sair\\?t=[0-9a-f]{32}")));

        // Bruno sai pelo link antes da vez dele; o envio de amanhã o pula
        String token = jdbc.queryForObject("select token_descadastro from tutor where id = ?", String.class, bruno);
        mvc.perform(post("/api/publico/descadastro").contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"" + token + "\"}"))
                .andExpect(status().isNoContent());
        assertNull(jdbc.queryForObject("select consentimento_marketing_em from tutor where id = ?", Object.class, bruno));
        assertNotNull(jdbc.queryForObject("select descadastrado_em from tutor where id = ?", Object.class, bruno));

        campanhas.continuar(); // o que roda sozinho às 10h
        mvc.perform(logado(get(BASE + "/campanhas")))
                .andExpect(jsonPath("$[0].status").value("CONCLUIDA"))
                .andExpect(jsonPath("$[0].envios.SIMULADA").value(1))
                .andExpect(jsonPath("$[0].envios.PULADO").value(1))
                .andExpect(jsonPath("$[0].envios.PENDENTE").value(0));
        mvc.perform(logado(get(BASE + "/campanhas/publico"))).andExpect(jsonPath("$.clientes").value(1));

        // o consentimento fica registrado no histórico (sem dado pessoal)
        mvc.perform(logado(get(BASE + "/financeiro/historico")))
                .andExpect(jsonPath("$[*].entidade", hasItems("consentimento-promocoes", "campanha")))
                .andExpect(content().string(not(containsString("Bruno"))));
    }

    @Test
    void descadastroNaoRevelaNadaEValidaCampanha() throws Exception {
        // token inexistente ou malformado: mesma resposta, nada muda
        mvc.perform(post("/api/publico/descadastro").contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"abc\"}"))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/publico/descadastro").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"00000000000000000000000000000000\"}"))
                .andExpect(status().isNoContent());
        // modelo da Meta com nome inválido; campanha só com login
        mvc.perform(logado(post(BASE + "/campanhas")).content(CAMPANHA.replace("promocao_loja", "Promoção Loja")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.template").value("Nome do modelo: só letras minúsculas, números e _"));
        mvc.perform(post(BASE + "/campanhas").contentType(MediaType.APPLICATION_JSON).content(CAMPANHA))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void pedeAvaliacaoNoGoogleUmaVezACada90Dias() throws Exception {
        long ana = cliente("Ana Souza", "(62) 99999-1111");
        long thor = json.readTree(mvc.perform(logado(post(BASE + "/tutores/" + ana + "/pets")).content("{\"nome\":\"Thor\",\"especie\":\"Cão\"}"))
                .andReturn().getResponse().getContentAsString()).get("id").asLong();
        String config = mvc.perform(logado(get(BASE + "/config"))).andReturn().getResponse().getContentAsString();
        mvc.perform(logado(put(BASE + "/config")).content(config.replace("\"linkAvaliacao\":\"\"", "\"linkAvaliacao\":\"https://g.page/r/abc/review\"")))
                .andExpect(status().isOk());
        mvc.perform(logado(put(BASE + "/whatsapp")).content("""
                {"lembretesAutomaticos":false,"respostaRapida":false,"textoRespostaRapida":"","pedirAvaliacao":true}
                """)).andExpect(jsonPath("$.pedirAvaliacao").value(true)).andExpect(jsonPath("$.temLinkAvaliacao").value(true));

        for (String hora : new String[]{"09:00", "11:00"}) {
            long ag = json.readTree(mvc.perform(logado(post(BASE + "/agenda")).content("""
                    {"petId":%d,"data":"2026-03-02","hora":"%s","tipo":"BANHO"}
                    """.formatted(thor, hora))).andReturn().getResponse().getContentAsString()).get("id").asLong();
            mvc.perform(logado(patch(BASE + "/agenda/" + ag + "/status")).content("{\"status\":\"CONCLUIDO\"}"));
        }
        mvc.perform(logado(get(BASE + "/whatsapp/mensagens")))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].tipo").value("AVALIACAO"))
                .andExpect(jsonPath("$[0].texto").value(containsString("https://g.page/r/abc/review")));
    }

    @Test
    void clienteQueAceitouAntesDoLinkExistirRecebeLinkValido() throws Exception {
        long ana = cliente("Ana Souza", "(62) 99999-1111");
        mvc.perform(logado(put(BASE + "/tutores/" + ana + "/promocoes")).content("{\"aceita\":true}"));
        jdbc.update("update tutor set token_descadastro = null where id = ?", ana); // como era antes da V11

        mvc.perform(logado(post(BASE + "/campanhas")).content(CAMPANHA)).andExpect(status().isCreated());
        mvc.perform(logado(get(BASE + "/whatsapp/mensagens")))
                .andExpect(jsonPath("$[0].texto").value(matchesPattern("(?s).*/sair\\?t=[0-9a-f]{32}")))
                .andExpect(jsonPath("$[0].texto").value(not(containsString("t=null"))));
    }
}
