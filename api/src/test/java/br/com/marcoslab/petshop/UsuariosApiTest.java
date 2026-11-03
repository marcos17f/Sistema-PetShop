package br.com.marcoslab.petshop;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Logins por pessoa: dono e funcionário, o que cada um acessa, senhas e conta principal de recuperação. */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class UsuariosApiTest {

    private static final String BASE = "/api/lojas/petshop-cleiton";

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    private MockHttpServletRequestBuilder como(String login, String senha, MockHttpServletRequestBuilder req) {
        return req.with(httpBasic(login, senha)).contentType(MediaType.APPLICATION_JSON);
    }

    private MockHttpServletRequestBuilder dono(MockHttpServletRequestBuilder req) {
        return como("loja", "senha-teste", req);
    }

    private long criarFuncionario() throws Exception {
        String r = mvc.perform(dono(post(BASE + "/usuarios")).content("""
                        {"nome":"Joana Banhista","login":"Joana","senha":"banho-e-tosa-2026","papel":"FUNCIONARIO"}
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.login").value("joana"))
                .andExpect(jsonPath("$.senha").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        return Long.parseLong(r.replaceAll(".*\"id\":(\\d+).*", "$1"));
    }

    @Test
    void funcionarioUsaAAgendaMasNaoVeDinheiroNemConfiguracoes() throws Exception {
        criarFuncionario();
        // senha guardada só como bcrypt
        assertTrue(jdbc.queryForObject("select senha_hash from usuario where login = 'joana'", String.class).startsWith("{bcrypt}$2"));

        mvc.perform(como("joana", "banho-e-tosa-2026", get(BASE + "/usuarios/eu")))
                .andExpect(jsonPath("$.papel").value("FUNCIONARIO"))
                .andExpect(jsonPath("$.nome").value("Joana Banhista"));
        mvc.perform(como("joana", "banho-e-tosa-2026", get(BASE + "/tutores"))).andExpect(status().isOk());
        mvc.perform(como("joana", "banho-e-tosa-2026", get(BASE + "/agenda").param("data", "2026-03-02"))).andExpect(status().isOk());
        mvc.perform(como("joana", "banho-e-tosa-2026", get(BASE + "/config"))).andExpect(status().isOk()); // nome da loja no menu

        for (String rota : new String[]{"/financeiro/resumo?mes=2026-03", "/precos", "/campanhas", "/avisos", "/usuarios"}) {
            mvc.perform(como("joana", "banho-e-tosa-2026", get(BASE + rota))).andExpect(status().isForbidden());
        }
        mvc.perform(como("joana", "banho-e-tosa-2026", put(BASE + "/config")).content("{}")).andExpect(status().isForbidden());
        mvc.perform(como("joana", "banho-e-tosa-2026", delete(BASE + "/tutores/1"))).andExpect(status().isForbidden());

        // o histórico mostra quem fez
        long tutor = Long.parseLong(mvc.perform(como("joana", "banho-e-tosa-2026", post(BASE + "/tutores")).content("""
                {"nome":"Ana","whatsapp":"(62) 99999-1234"}
                """)).andReturn().getResponse().getContentAsString().replaceAll(".*\"id\":(\\d+).*", "$1"));
        mvc.perform(como("joana", "banho-e-tosa-2026", put(BASE + "/tutores/" + tutor + "/promocoes")).content("{\"aceita\":true}"));
        mvc.perform(dono(get(BASE + "/financeiro/historico"))).andExpect(jsonPath("$[0].usuario").value("joana"));
    }

    @Test
    void donoDesativaRedefineSenhaENaoSeTrancaFora() throws Exception {
        long joana = criarFuncionario();
        // login repetido (inclusive o da conta principal) e senha fraca
        mvc.perform(dono(post(BASE + "/usuarios")).content("""
                {"nome":"Outra","login":"JOANA","senha":"outra-senha-123","papel":"FUNCIONARIO"}
                """)).andExpect(status().isConflict());
        mvc.perform(dono(post(BASE + "/usuarios")).content("""
                {"nome":"Outra","login":"loja","senha":"outra-senha-123","papel":"DONO"}
                """)).andExpect(status().isConflict());
        mvc.perform(dono(post(BASE + "/usuarios")).content("""
                {"nome":"Pedro","login":"pedro","senha":"curta","papel":"FUNCIONARIO"}
                """)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.campos.senha").value("A senha precisa ter de 10 a 72 caracteres"));

        // esqueceu a senha: o dono redefine
        mvc.perform(dono(put(BASE + "/usuarios/" + joana + "/senha")).content("{\"senha\":\"outra-senha-forte-7\"}"))
                .andExpect(status().isNoContent());
        mvc.perform(como("joana", "banho-e-tosa-2026", get(BASE + "/tutores"))).andExpect(status().isUnauthorized());
        mvc.perform(como("joana", "outra-senha-forte-7", get(BASE + "/tutores"))).andExpect(status().isOk());

        // desativada não entra
        mvc.perform(dono(put(BASE + "/usuarios/" + joana)).content("""
                {"nome":"Joana Banhista","papel":"FUNCIONARIO","ativo":false}
                """)).andExpect(jsonPath("$.ativo").value(false));
        mvc.perform(como("joana", "outra-senha-forte-7", get(BASE + "/tutores"))).andExpect(status().isUnauthorized());

        // um dono cadastrado não pode desativar a própria conta
        mvc.perform(dono(post(BASE + "/usuarios")).content("""
                {"nome":"Cleiton","login":"cleiton","senha":"pet-shop-forte-99","papel":"DONO"}
                """)).andExpect(status().isCreated());
        long cleiton = jdbc.queryForObject("select id from usuario where login = 'cleiton'", Long.class);
        mvc.perform(como("cleiton", "pet-shop-forte-99", put(BASE + "/usuarios/" + cleiton)).content("""
                {"nome":"Cleiton","papel":"DONO","ativo":false}
                """)).andExpect(status().isConflict());
        mvc.perform(como("cleiton", "pet-shop-forte-99", get(BASE + "/financeiro/resumo").param("mes", "2026-03"))).andExpect(status().isOk());
    }

    @Test
    void cadaUmTrocaAPropriaSenha() throws Exception {
        criarFuncionario();
        mvc.perform(como("joana", "banho-e-tosa-2026", put(BASE + "/usuarios/eu/senha")).content("""
                {"atual":"errada","nova":"minha-senha-nova-1"}
                """)).andExpect(status().isConflict()).andExpect(jsonPath("$.detail").value("A senha atual não confere."));
        mvc.perform(como("joana", "banho-e-tosa-2026", put(BASE + "/usuarios/eu/senha")).content("""
                {"atual":"banho-e-tosa-2026","nova":"joana-1234567"}
                """)).andExpect(status().isConflict()).andExpect(jsonPath("$.detail").value("A senha não pode conter o login."));
        mvc.perform(como("joana", "banho-e-tosa-2026", put(BASE + "/usuarios/eu/senha")).content("""
                {"atual":"banho-e-tosa-2026","nova":"minha-senha-nova-1"}
                """)).andExpect(status().isNoContent());
        mvc.perform(como("joana", "minha-senha-nova-1", get(BASE + "/usuarios/eu"))).andExpect(status().isOk());
        // a conta principal do servidor não troca pela tela
        mvc.perform(dono(put(BASE + "/usuarios/eu/senha")).content("""
                {"atual":"senha-teste","nova":"qualquer-coisa-123"}
                """)).andExpect(status().isConflict()).andExpect(jsonPath("$.detail").value(containsString("no servidor")));
        mvc.perform(dono(get(BASE + "/usuarios/eu"))).andExpect(jsonPath("$.contaPrincipal").value(true));
    }
}
