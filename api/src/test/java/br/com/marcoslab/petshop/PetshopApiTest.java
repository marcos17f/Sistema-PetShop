package br.com.marcoslab.petshop;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDate;

import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Fluxo completo do painel contra a API real (H2): cliente → pet → vacina → agenda → lembretes. */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class PetshopApiTest {

    private static final String BASE = "/api/lojas/petshop-cleiton";

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    private MockHttpServletRequestBuilder logado(MockHttpServletRequestBuilder req) {
        return req.with(httpBasic("loja", "senha-teste")).contentType(MediaType.APPLICATION_JSON);
    }

    private JsonNode criar(String caminho, String corpo) throws Exception {
        String resposta = mvc.perform(logado(post(BASE + caminho)).content(corpo))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(resposta);
    }

    private long novoTutorComPet() throws Exception {
        long tutorId = criar("/tutores", """
                {"nome":"Ana Paula Souza","whatsapp":"(62) 99999-1234","endereco":"","cpf":"","origem":"Instagram"}
                """).get("id").asLong();
        return criar("/tutores/" + tutorId + "/pets", """
                {"nome":"Thor","especie":"Cão","raca":"Shih-tzu","pesoKg":6.5,"observacoes":"","intervaloBanhoDias":15}
                """).get("id").asLong();
    }

    @Test
    void semLoginResponde401() throws Exception {
        mvc.perform(get(BASE + "/tutores")).andExpect(status().isUnauthorized());
        mvc.perform(get(BASE + "/tutores").with(httpBasic("loja", "errada"))).andExpect(status().isUnauthorized());
    }

    @Test
    void outraLojaNaoExiste() throws Exception {
        mvc.perform(logado(get("/api/lojas/outra-loja/tutores")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Loja não encontrada"));
    }

    @Test
    void cadastraClienteEPetEBuscaPeloNomeDoPet() throws Exception {
        novoTutorComPet();

        mvc.perform(logado(get(BASE + "/tutores").param("busca", "tho")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].whatsapp").value("62999991234"))
                .andExpect(jsonPath("$[0].cpf").value(""))
                .andExpect(jsonPath("$[0].pets[0].nome").value("Thor"))
                .andExpect(jsonPath("$[0].pets[0].ultimoBanho").value(nullValue()));

        mvc.perform(logado(get(BASE + "/tutores").param("busca", "ninguém")))
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void validaCampos() throws Exception {
        mvc.perform(logado(post(BASE + "/tutores")).content("""
                        {"nome":"","whatsapp":"12"}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Confira os campos destacados."))
                .andExpect(jsonPath("$.campos.nome").exists())
                .andExpect(jsonPath("$.campos.whatsapp").exists());
    }

    @Test
    void banhoConcluidoAtualizaUltimoBanhoEGeraLembreteDeRetorno() throws Exception {
        long petId = novoTutorComPet();
        LocalDate dia = LocalDate.of(2026, 3, 2);

        long agId = criar("/agenda", """
                {"petId":%d,"data":"%s","hora":"09:30","tipo":"BANHO","descricao":""}
                """.formatted(petId, dia)).get("id").asLong();

        // mesmo pet, mesmo horário: conflito
        mvc.perform(logado(post(BASE + "/agenda")).content("""
                        {"petId":%d,"data":"%s","hora":"09:30","tipo":"TOSA"}
                        """.formatted(petId, dia)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Esse pet já tem atendimento nesse horário."));

        mvc.perform(logado(get(BASE + "/agenda").param("data", dia.toString())))
                .andExpect(jsonPath("$[0].hora").value("09:30"))
                .andExpect(jsonPath("$[0].petNome").value("Thor"))
                .andExpect(jsonPath("$[0].tutorNome").value("Ana Paula Souza"))
                .andExpect(jsonPath("$[0].status").value("AGUARDANDO"));

        mvc.perform(logado(patch(BASE + "/agenda/" + agId + "/status")).content("{\"status\":\"CONCLUIDO\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONCLUIDO"));

        LocalDate retorno = dia.plusDays(15);
        mvc.perform(logado(get(BASE + "/lembretes").param("data", retorno.toString())))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].tipo").value("BANHO"))
                .andExpect(jsonPath("$[0].chave").value("BANHO:" + petId + ":" + retorno))
                .andExpect(jsonPath("$[0].mensagem").value(
                        "Olá, Ana! Amanhã completa 15 dias do último banho do Thor. Quer agendar o próximo?"))
                .andExpect(jsonPath("$[0].enviado").value(false));
    }

    @Test
    void vacinaGeraLembreteQueFicaMarcadoComoEnviado() throws Exception {
        long petId = novoTutorComPet();
        LocalDate reforco = LocalDate.of(2026, 12, 10);

        criar("/pets/" + petId + "/vacinas", """
                {"vacina":"V10","dose":"Reforço anual","aplicadaEm":"2025-12-10","proximaEm":"%s"}
                """.formatted(reforco));

        mvc.perform(logado(get(BASE + "/pets/" + petId + "/vacinas")))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].vacina").value("V10"));

        String lista = mvc.perform(logado(get(BASE + "/lembretes").param("data", reforco.toString())))
                .andExpect(jsonPath("$[0].mensagem").value(
                        "Olá, Ana! Amanhã (10/12) é dia da vacina V10 (Reforço anual) do Thor. Vamos agendar o horário?"))
                .andReturn().getResponse().getContentAsString();
        String chave = json.readTree(lista).get(0).get("chave").asText();

        for (int i = 0; i < 2; i++) { // marcar duas vezes não dá erro
            mvc.perform(logado(post(BASE + "/lembretes/enviados")).content("{\"chave\":\"" + chave + "\"}"))
                    .andExpect(status().isNoContent());
        }

        mvc.perform(logado(get(BASE + "/lembretes").param("data", reforco.toString())))
                .andExpect(jsonPath("$[0].enviado").value(true));
    }

    @Test
    void reforcoAntesDaAplicacaoEhRecusado() throws Exception {
        long petId = novoTutorComPet();
        mvc.perform(logado(post(BASE + "/pets/" + petId + "/vacinas")).content("""
                        {"vacina":"V10","aplicadaEm":"2025-12-10","proximaEm":"2025-01-01"}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.reforcoDepois").exists());
    }
}
