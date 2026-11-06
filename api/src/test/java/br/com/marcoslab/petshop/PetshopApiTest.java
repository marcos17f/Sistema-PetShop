package br.com.marcoslab.petshop;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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

    @Autowired
    JdbcTemplate jdbc;

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
                .andExpect(jsonPath("$.detail").value("Muito perto do atendimento das 09:30. Deixe pelo menos 40 minutos entre um atendimento e outro."));

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
                        "Olá, Ana! Aqui é da A Arca Pet. Amanhã completa 15 dias do último banho do Thor. Quer agendar o próximo?"))
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
                        "Olá, Ana! Aqui é da A Arca Pet. Amanhã (10/12) é dia da vacina V10 (Reforço anual) do Thor. Vamos agendar o horário?"))
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

    private long tutorDoPet(long petId) {
        return jdbc.queryForObject("select tutor_id from pet where id = ?", Long.class, petId);
    }

    private int contar(String tabela) {
        return jdbc.queryForObject("select count(*) from " + tabela, Integer.class);
    }

    @Test
    void editaAgendamentoRespeitandoIntervaloEExcluiLancadoPorEngano() throws Exception {
        long thor = novoTutorComPet();
        String ag = """
                {"petId":%d,"data":"2026-03-02","hora":"%s","tipo":"%s","descricao":"%s"}
                """;
        long a1 = criar("/agenda", ag.formatted(thor, "10:00", "BANHO", "")).get("id").asLong();
        long a2 = criar("/agenda", ag.formatted(thor, "14:00", "BANHO", "")).get("id").asLong();

        // remarcar para perto de outro atendimento: recusado
        mvc.perform(logado(put(BASE + "/agenda/" + a2)).content(ag.formatted(thor, "10:20", "BANHO", "")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(containsString("atendimento das 10:00")));
        // manter o próprio horário não conflita com ele mesmo; serviço e detalhe mudam
        mvc.perform(logado(put(BASE + "/agenda/" + a2)).content(ag.formatted(thor, "14:00", "TOSA", "Hidratação")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipo").value("TOSA"))
                .andExpect(jsonPath("$.descricao").value("Hidratação"));
        mvc.perform(logado(put(BASE + "/agenda/" + a2)).content(ag.formatted(thor, "15:30", "TOSA", "")))
                .andExpect(jsonPath("$.hora").value("15:30"));

        // concluído já está no financeiro: não edita nem exclui
        mvc.perform(logado(patch(BASE + "/agenda/" + a1 + "/status")).content("{\"status\":\"CONCLUIDO\"}"));
        mvc.perform(logado(put(BASE + "/agenda/" + a1)).content(ag.formatted(thor, "11:00", "BANHO", "")))
                .andExpect(status().isConflict());
        mvc.perform(logado(delete(BASE + "/agenda/" + a1)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(containsString("não pode ser excluído")));

        // lançado por engano: exclui
        mvc.perform(logado(delete(BASE + "/agenda/" + a2))).andExpect(status().isNoContent());
        mvc.perform(logado(get(BASE + "/agenda").param("data", "2026-03-02"))).andExpect(jsonPath("$", hasSize(1)));
        mvc.perform(logado(delete(BASE + "/agenda/" + a2))).andExpect(status().isNotFound());
    }

    @Test
    void editaClienteEApagaPorCompletoCadastroFeitoPorEngano() throws Exception {
        long thor = novoTutorComPet();
        long tutor = tutorDoPet(thor);
        criar("/pets/" + thor + "/vacinas", """
                {"vacina":"V10","aplicadaEm":"2026-01-10","proximaEm":"2027-01-10"}
                """);
        criar("/agenda", """
                {"petId":%d,"data":"2026-12-01","hora":"10:00","tipo":"BANHO"}
                """.formatted(thor));

        mvc.perform(logado(put(BASE + "/tutores/" + tutor)).content("""
                        {"nome":"Ana Paula Lima","whatsapp":"(62) 98888-7777","endereco":"Rua 1","cpf":"","origem":"Google"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Ana Paula Lima"))
                .andExpect(jsonPath("$.whatsapp").value("62988887777"))
                .andExpect(jsonPath("$.origem").value("Google"))
                .andExpect(jsonPath("$.pets[0].nome").value("Thor"));
        mvc.perform(logado(put(BASE + "/tutores/" + tutor)).content("{\"nome\":\"Ana\",\"whatsapp\":\"abc\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.whatsapp").value("WhatsApp inválido"));

        mvc.perform(logado(delete(BASE + "/tutores/" + tutor))).andExpect(status().isNoContent());

        mvc.perform(logado(get(BASE + "/tutores/" + tutor))).andExpect(status().isNotFound());
        assertEquals(0, contar("tutor"));
        assertEquals(0, contar("pet"));
        assertEquals(0, contar("aplicacao_vacina"));
        assertEquals(0, contar("agendamento"));
    }

    @Test
    void excluirClienteComAtendimentoPagoApagaDadosPessoaisEMantemAReceita() throws Exception {
        long thor = novoTutorComPet();
        long tutor = tutorDoPet(thor);
        long pago = criar("/agenda", """
                {"petId":%d,"data":"2026-03-02","hora":"09:00","tipo":"BANHO"}
                """.formatted(thor)).get("id").asLong();
        mvc.perform(logado(patch(BASE + "/agenda/" + pago + "/status")).content("{\"status\":\"CONCLUIDO\"}"));
        LocalDate futuro = LocalDate.now(ZoneId.of("America/Sao_Paulo")).plusDays(5);
        long marcado = criar("/agenda", """
                {"petId":%d,"data":"%s","hora":"10:00","tipo":"BANHO"}
                """.formatted(thor, futuro)).get("id").asLong();

        mvc.perform(logado(delete(BASE + "/tutores/" + tutor))).andExpect(status().isNoContent());

        // some do painel e não recebe mais agendamento
        mvc.perform(logado(get(BASE + "/tutores/" + tutor))).andExpect(status().isNotFound());
        mvc.perform(logado(get(BASE + "/tutores").param("busca", "thor"))).andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(logado(post(BASE + "/agenda")).content("""
                        {"petId":%d,"data":"%s","hora":"15:00","tipo":"BANHO"}
                        """.formatted(thor, futuro)))
                .andExpect(status().isNotFound());

        // o valor recebido continua no financeiro, sem o nome
        mvc.perform(logado(get(BASE + "/financeiro/atendimentos").param("mes", "2026-03")))
                .andExpect(jsonPath("$[0].tutorNome").value("Cliente removido"))
                .andExpect(jsonPath("$[0].valor").value(50.00));
        // dados pessoais apagados; horário futuro liberado
        Map<String, Object> linha = jdbc.queryForMap("select nome, whatsapp, endereco, cpf from tutor where id = ?", tutor);
        assertEquals("Cliente removido", linha.get("nome"));
        assertEquals("", linha.get("whatsapp"));
        assertNull(linha.get("endereco"));
        assertNull(linha.get("cpf"));
        assertEquals("CANCELADO", jdbc.queryForObject("select status from agendamento where id = ?", String.class, marcado));
    }

    @Test
    void editaEExcluiVacinaRegistradaErrado() throws Exception {
        long thor = novoTutorComPet();
        long v = criar("/pets/" + thor + "/vacinas", """
                {"vacina":"V8","dose":"1ª dose","aplicadaEm":"2026-01-10","proximaEm":"2026-02-10"}
                """).get("id").asLong();

        mvc.perform(logado(put(BASE + "/pets/" + thor + "/vacinas/" + v)).content("""
                        {"vacina":"V10","dose":"2ª dose","aplicadaEm":"2026-01-12","proximaEm":"2026-02-12"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vacina").value("V10"))
                .andExpect(jsonPath("$.dose").value("2ª dose"))
                .andExpect(jsonPath("$.proximaEm").value("2026-02-12"));
        // mesma validação do cadastro
        mvc.perform(logado(put(BASE + "/pets/" + thor + "/vacinas/" + v)).content("""
                        {"vacina":"V10","aplicadaEm":"2026-01-12","proximaEm":"2026-01-01"}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.reforcoDepois").exists());

        // a vacina precisa ser do pet da URL
        long tutor2 = criar("/tutores", """
                {"nome":"Bruno Lima","whatsapp":"(62) 98888-0000"}
                """).get("id").asLong();
        long luna = criar("/tutores/" + tutor2 + "/pets", """
                {"nome":"Luna","especie":"Gato"}
                """).get("id").asLong();
        mvc.perform(logado(delete(BASE + "/pets/" + luna + "/vacinas/" + v))).andExpect(status().isNotFound());

        mvc.perform(logado(delete(BASE + "/pets/" + thor + "/vacinas/" + v))).andExpect(status().isNoContent());
        mvc.perform(logado(get(BASE + "/pets/" + thor + "/vacinas"))).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void editaPetComUltimoBanhoEExcluiPetCadastradoPorEngano() throws Exception {
        long thor = novoTutorComPet();
        long tutor = tutorDoPet(thor);
        String pet = "/tutores/" + tutor + "/pets/";

        mvc.perform(logado(put(BASE + pet + thor)).content("""
                        {"nome":"Thor","especie":"Cão","raca":"Lhasa","pesoKg":7.2,"observacoes":"Medroso",
                         "intervaloBanhoDias":20,"ultimoBanho":"2026-03-01"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.raca").value("Lhasa"))
                .andExpect(jsonPath("$.intervaloBanhoDias").value(20))
                .andExpect(jsonPath("$.ultimoBanho").value("2026-03-01"));
        // o último banho corrigido já gera o lembrete do próximo
        mvc.perform(logado(get(BASE + "/lembretes").param("data", "2026-03-21")))
                .andExpect(jsonPath("$[0].tipo").value("BANHO"));
        // mensagem de campo clara (antes: "Intervalo inválido" com 1515)
        mvc.perform(logado(put(BASE + pet + thor)).content("""
                        {"nome":"Thor","especie":"Cão","intervaloBanhoDias":1515}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.intervaloBanhoDias").value("O banho deve ser a cada 1 a 365 dias"));

        // pet de outro cliente pela URL errada: 404
        mvc.perform(logado(delete(BASE + "/tutores/999/pets/" + thor))).andExpect(status().isNotFound());

        // com atendimento concluído não exclui
        long ag = criar("/agenda", """
                {"petId":%d,"data":"2026-03-02","hora":"09:00","tipo":"BANHO"}
                """.formatted(thor)).get("id").asLong();
        mvc.perform(logado(patch(BASE + "/agenda/" + ag + "/status")).content("{\"status\":\"CONCLUIDO\"}"));
        mvc.perform(logado(delete(BASE + pet + thor))).andExpect(status().isConflict());

        // pet cadastrado por engano: apaga com vacinas e agendamentos
        long engano = criar("/tutores/" + tutor + "/pets", """
                {"nome":"Rex","especie":"Cão","ultimoBanho":"2026-02-01"}
                """).get("id").asLong();
        criar("/pets/" + engano + "/vacinas", """
                {"vacina":"V10","aplicadaEm":"2026-01-10"}
                """);
        criar("/agenda", """
                {"petId":%d,"data":"2026-03-02","hora":"11:00","tipo":"TOSA"}
                """.formatted(engano));
        mvc.perform(logado(delete(BASE + pet + engano))).andExpect(status().isNoContent());
        assertEquals(1, contar("pet"));
        assertEquals(0, contar("aplicacao_vacina"));
        assertEquals(1, contar("agendamento"));
    }

    @Test
    void bloqueiaOEnderecoDepoisDeDezSenhasErradas() throws Exception {
        java.util.function.Function<String, org.springframework.test.web.servlet.request.RequestPostProcessor> doIp =
                ip -> r -> { r.setRemoteAddr(ip); return r; };
        for (int i = 0; i < 10; i++) {
            mvc.perform(get(BASE + "/tutores").with(doIp.apply("10.0.0.7")).with(httpBasic("loja", "chute" + i)))
                    .andExpect(status().isUnauthorized());
        }
        // agora nem a senha certa entra desse endereço
        mvc.perform(get(BASE + "/tutores").with(doIp.apply("10.0.0.7")).with(httpBasic("loja", "senha-teste")))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.detail").value(containsString("Muitas tentativas")));
        // outro endereço (a loja) continua entrando
        mvc.perform(get(BASE + "/tutores").with(doIp.apply("10.0.0.8")).with(httpBasic("loja", "senha-teste")))
                .andExpect(status().isOk());
    }

    @Test
    void configuracoesDaLojaValemNoSitePublicoENaAgenda() throws Exception {
        String config = """
                {"nome":"Pet Shop Bom Pelo","whatsapp":"(62) 3333-4444","cidade":"Goiânia – GO","endereco":"Av. T-63, 100",
                 "horarioTexto":"Seg a sex, 7h às 17h","instagram":"bompelo","linkAvaliacao":"https://g.page/r/abc/review",
                 "intervaloAgendaMinutos":30,"abre":"07:00","fecha":"17:00","sabadoAbre":null,"sabadoFecha":null}
                """;
        mvc.perform(logado(put(BASE + "/config")).content(config))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.whatsapp").value("6233334444"))
                .andExpect(jsonPath("$.sabadoAbre").value(nullValue()));

        // o site público lê sem login (e só dados da loja)
        mvc.perform(get("/api/publico/lojas/petshop-cleiton"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Pet Shop Bom Pelo"))
                .andExpect(jsonPath("$.abre").value("07:00"))
                .andExpect(jsonPath("$.linkAvaliacao").value("https://g.page/r/abc/review"));
        // configurações só com login; público é só leitura
        mvc.perform(get(BASE + "/config")).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/publico/lojas/petshop-cleiton").contentType(MediaType.APPLICATION_JSON).content(config))
                .andExpect(status().isUnauthorized());

        // validações
        mvc.perform(logado(put(BASE + "/config")).content(config.replace("\"fecha\":\"17:00\"", "\"fecha\":\"06:00\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.horarioValido").value("O horário de fechar precisa ser depois do de abrir"));
        mvc.perform(logado(put(BASE + "/config")).content(config.replace("https://g.page", "http://g.page")))
                .andExpect(jsonPath("$.campos.linkAvaliacao").value("O link precisa começar com https://"));

        // o intervalo novo (30 min) já vale na agenda
        long thor = novoTutorComPet();
        criar("/agenda", """
                {"petId":%d,"data":"2026-03-02","hora":"09:00","tipo":"BANHO"}
                """.formatted(thor));
        criar("/agenda", """
                {"petId":%d,"data":"2026-03-02","hora":"09:30","tipo":"TOSA"}
                """.formatted(thor));
    }

    @Test
    void fichaMostraHistoricoEValorGastoDoCliente() throws Exception {
        long thor = novoTutorComPet();
        long tutor = tutorDoPet(thor);
        long mel = criar("/tutores/" + tutor + "/pets", "{\"nome\":\"Mel\",\"especie\":\"Gato\"}").get("id").asLong();
        String ag = "{\"petId\":%d,\"data\":\"%s\",\"hora\":\"%s\",\"tipo\":\"%s\"}";
        long a1 = criar("/agenda", ag.formatted(thor, "2026-02-10", "09:00", "BANHO")).get("id").asLong();
        long a2 = criar("/agenda", ag.formatted(mel, "2026-03-05", "10:00", "TOSA")).get("id").asLong();
        criar("/agenda", ag.formatted(thor, "2026-12-01", "09:00", "BANHO")); // futuro, aguardando: não soma
        mvc.perform(logado(patch(BASE + "/agenda/" + a1 + "/status")).content("{\"status\":\"CONCLUIDO\"}")); // preço padrão 50
        mvc.perform(logado(patch(BASE + "/agenda/" + a2 + "/status")).content("{\"status\":\"CONCLUIDO\",\"valor\":70.50}"));

        mvc.perform(logado(get(BASE + "/agenda/cliente/" + tutor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalGasto").value(120.50))
                .andExpect(jsonPath("$.atendimentosConcluidos").value(2))
                .andExpect(jsonPath("$.ultimoAtendimento").value("2026-03-05"))
                .andExpect(jsonPath("$.atendimentos", hasSize(3)))
                .andExpect(jsonPath("$.atendimentos[0].data").value("2026-12-01"))
                .andExpect(jsonPath("$.atendimentos[1].petNome").value("Mel"));
        mvc.perform(logado(get(BASE + "/agenda/cliente/999"))).andExpect(status().isNotFound());
    }
}
