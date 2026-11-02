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

import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Financeiro contra a API real (H2): lançamentos, recorrência, receitas de atendimento e resumo do mês. */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class FinanceiroApiTest {

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
        return json.readTree(mvc.perform(logado(post(BASE + caminho)).content(corpo))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
    }

    /** Id de uma categoria padrão (criada na migration V3). */
    private long categoria(String tipo, String nome) {
        return jdbc.queryForObject("select id from categoria_financeira where loja_id = 1 and tipo = ? and nome = ?",
                Long.class, tipo, nome);
    }

    private long lancar(String data, long categoriaId, String valor, boolean recorrente) throws Exception {
        return criar("/financeiro/lancamentos", """
                {"data":"%s","categoriaId":%d,"descricao":"","valor":%s,"recorrente":%s}
                """.formatted(data, categoriaId, valor, recorrente)).get("id").asLong();
    }

    private long agendamentoConcluido(long petId, String data, String hora, String tipo, String valor) throws Exception {
        long id = criar("/agenda", """
                {"petId":%d,"data":"%s","hora":"%s","tipo":"%s"}
                """.formatted(petId, data, hora, tipo)).get("id").asLong();
        String corpo = valor == null ? "{\"status\":\"CONCLUIDO\"}" : "{\"status\":\"CONCLUIDO\",\"valor\":" + valor + "}";
        mvc.perform(logado(patch(BASE + "/agenda/" + id + "/status")).content(corpo)).andExpect(status().isOk());
        return id;
    }

    private long novoPet() throws Exception {
        long tutorId = criar("/tutores", """
                {"nome":"Ana Paula Souza","whatsapp":"(62) 99999-1234"}
                """).get("id").asLong();
        return criar("/tutores/" + tutorId + "/pets", """
                {"nome":"Thor","especie":"Cão"}
                """).get("id").asLong();
    }

    @Test
    void categoriasPadraoECriacaoSemRepetir() throws Exception {
        mvc.perform(logado(get(BASE + "/financeiro/categorias")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(18)))
                .andExpect(jsonPath("$[?(@.tipo == 'FIXO')].nome", hasItems("Aluguel", "Energia", "Água", "Funcionários", "Impostos")))
                .andExpect(jsonPath("$[?(@.tipo == 'VARIAVEL')].nome", hasItems("Freelancers", "Gasolina da moto")))
                .andExpect(jsonPath("$[?(@.tipo == 'EVENTUAL')].nome", hasItems("Equipamentos", "Conserto da moto")));

        criar("/financeiro/categorias", "{\"nome\":\"Internet\",\"tipo\":\"FIXO\"}");
        mvc.perform(logado(post(BASE + "/financeiro/categorias")).content("{\"nome\":\" internet \",\"tipo\":\"FIXO\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Já existe uma categoria com esse nome."));
    }

    @Test
    void lancamentoCriaEditaExcluiEValida() throws Exception {
        long energia = categoria("FIXO", "Energia");
        long gasolina = categoria("VARIAVEL", "Gasolina da moto");
        long conserto = categoria("EVENTUAL", "Conserto da moto");

        long id = lancar("2026-03-10", energia, "180.35", true);
        mvc.perform(logado(get(BASE + "/financeiro/lancamentos").param("mes", "2026-03")))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].tipo").value("FIXO"))
                .andExpect(jsonPath("$[0].categoriaNome").value("Energia"))
                .andExpect(jsonPath("$[0].valor").value(180.35))
                .andExpect(jsonPath("$[0].recorrente").value(true));

        // despesa variável também pode repetir todo mês
        mvc.perform(logado(put(BASE + "/financeiro/lancamentos/" + id)).content("""
                        {"data":"2026-03-11","categoriaId":%d,"descricao":"Busca do Thor","valor":25,"recorrente":true}
                        """.formatted(gasolina)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipo").value("VARIAVEL"))
                .andExpect(jsonPath("$.descricao").value("Busca do Thor"))
                .andExpect(jsonPath("$.recorrente").value(true));

        // trocar para eventual tira a recorrência (gasto pontual não repete)
        mvc.perform(logado(put(BASE + "/financeiro/lancamentos/" + id)).content("""
                        {"data":"2026-03-11","categoriaId":%d,"descricao":"Corrente nova","valor":25,"recorrente":true}
                        """.formatted(conserto)))
                .andExpect(jsonPath("$.tipo").value("EVENTUAL"))
                .andExpect(jsonPath("$.recorrente").value(false));

        mvc.perform(logado(post(BASE + "/financeiro/lancamentos")).content("""
                        {"data":"2026-03-10","categoriaId":%d,"valor":0}
                        """.formatted(energia)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.valor").value("O valor precisa ser maior que zero"));
        mvc.perform(logado(post(BASE + "/financeiro/lancamentos")).content("""
                        {"data":"2026-03-10","categoriaId":%d,"valor":10.555}
                        """.formatted(energia)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.valor").value("Use no máximo 2 casas decimais"));

        mvc.perform(logado(delete(BASE + "/financeiro/lancamentos/" + id))).andExpect(status().isNoContent());
        mvc.perform(logado(get(BASE + "/financeiro/lancamentos").param("mes", "2026-03")))
                .andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(logado(delete(BASE + "/financeiro/lancamentos/" + id))).andExpect(status().isNotFound());
    }

    @Test
    void naoUsaCategoriaNemVeLancamentoDeOutraLoja() throws Exception {
        jdbc.update("insert into loja (id, slug, nome) values (2, 'outra-loja', 'Outra')");
        jdbc.update("insert into categoria_financeira (id, loja_id, nome, tipo) values (900, 2, 'Aluguel', 'FIXO')");
        jdbc.update("""
                insert into lancamento_financeiro (id, loja_id, categoria_id, data, tipo, valor, recorrente, criado_em)
                values (901, 2, 900, date '2026-03-05', 'FIXO', 999.00, false, current_timestamp)
                """);

        mvc.perform(logado(post(BASE + "/financeiro/lancamentos")).content("""
                        {"data":"2026-03-10","categoriaId":900,"valor":10}
                        """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Categoria não encontrada"));
        mvc.perform(logado(get(BASE + "/financeiro/lancamentos").param("mes", "2026-03")))
                .andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(logado(delete(BASE + "/financeiro/lancamentos/901"))).andExpect(status().isNotFound());
        mvc.perform(logado(get("/api/lojas/outra-loja/financeiro/resumo").param("mes", "2026-03")))
                .andExpect(status().isNotFound());
    }

    @Test
    void recorrenteECopiadoUmaVezPorMesEExcluirACopiaEncerra() throws Exception {
        long aluguel = categoria("FIXO", "Aluguel");
        lancar("2026-01-05", aluguel, "1500.00", true);

        // abrir março copia para fevereiro e março; abrir de novo não duplica
        for (int i = 0; i < 2; i++) {
            mvc.perform(logado(get(BASE + "/financeiro/lancamentos").param("mes", "2026-03")))
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].data").value("2026-03-05"))
                    .andExpect(jsonPath("$[0].valor").value(1500.00));
        }
        mvc.perform(logado(get(BASE + "/financeiro/lancamentos").param("mes", "2026-02")))
                .andExpect(jsonPath("$", hasSize(1)));

        long marco = json.readTree(mvc.perform(logado(get(BASE + "/financeiro/lancamentos").param("mes", "2026-03")))
                .andReturn().getResponse().getContentAsString()).get(0).get("id").asLong();
        mvc.perform(logado(delete(BASE + "/financeiro/lancamentos/" + marco))).andExpect(status().isNoContent());

        mvc.perform(logado(get(BASE + "/financeiro/lancamentos").param("mes", "2026-03")))
                .andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(logado(get(BASE + "/financeiro/lancamentos").param("mes", "2026-02")))
                .andExpect(jsonPath("$[0].recorrente").value(false));
    }

    @Test
    void concluirUsaPrecoPadraoOuOValorInformado() throws Exception {
        long petId = novoPet();
        mvc.perform(logado(put(BASE + "/precos/BANHO")).content("{\"valor\":55.00}"))
                .andExpect(status().isOk());
        mvc.perform(logado(get(BASE + "/precos")))
                .andExpect(jsonPath("$", hasSize(5)))
                .andExpect(jsonPath("$[?(@.tipo == 'BANHO')].valor", contains(55.00)));

        agendamentoConcluido(petId, "2026-03-02", "09:00", "BANHO", null);
        agendamentoConcluido(petId, "2026-03-02", "10:00", "TOSA", "70.50");

        mvc.perform(logado(get(BASE + "/financeiro/atendimentos").param("mes", "2026-03")))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].valor").value(55.00))
                .andExpect(jsonPath("$[1].valor").value(70.50));
    }

    @Test
    void historicoRegistraQuemMudouOQueENaoTemRotaParaApagar() throws Exception {
        long energia = categoria("FIXO", "Energia");
        long id = lancar("2026-03-10", energia, "180.35", false);
        mvc.perform(logado(put(BASE + "/financeiro/lancamentos/" + id)).content("""
                {"data":"2026-03-10","categoriaId":%d,"descricao":"Conta de luz","valor":200,"recorrente":false}
                """.formatted(energia))).andExpect(status().isOk());
        mvc.perform(logado(delete(BASE + "/financeiro/lancamentos/" + id))).andExpect(status().isNoContent());
        mvc.perform(logado(put(BASE + "/precos/BANHO")).content("{\"valor\":55}")).andExpect(status().isOk());
        mvc.perform(logado(put(BASE + "/precos/BANHO")).content("{\"valor\":55.00}")).andExpect(status().isOk()); // igual: não registra

        mvc.perform(logado(get(BASE + "/financeiro/historico")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(4)))
                .andExpect(jsonPath("$[*].usuario", everyItem(is("loja"))))
                .andExpect(jsonPath("$[0].entidade").value("preco"))
                .andExpect(jsonPath("$[0].antes").value("R$ 50.00"))
                .andExpect(jsonPath("$[0].depois").value("R$ 55"))
                .andExpect(jsonPath("$[1].acao").value("EXCLUIU"))
                .andExpect(jsonPath("$[1].antes").value("2026-03-10 · FIXO · Energia · R$ 200.00 · Conta de luz"))
                .andExpect(jsonPath("$[1].depois").value(nullValue()))
                .andExpect(jsonPath("$[2].acao").value("ALTEROU"))
                .andExpect(jsonPath("$[2].antes").value("2026-03-10 · FIXO · Energia · R$ 180.35"))
                .andExpect(jsonPath("$[3].acao").value("CRIOU"));

        mvc.perform(logado(delete(BASE + "/financeiro/historico"))).andExpect(status().isMethodNotAllowed());
        mvc.perform(logado(put(BASE + "/financeiro/historico"))).andExpect(status().isMethodNotAllowed());
    }

    @Test
    void valorCobradoNoAtendimentoEntraNoHistoricoSemDadoDoCliente() throws Exception {
        long agId = agendamentoConcluido(novoPet(), "2026-03-02", "09:00", "BANHO", null);
        mvc.perform(logado(patch(BASE + "/agenda/" + agId + "/status")).content("{\"status\":\"CONCLUIDO\",\"valor\":65}"))
                .andExpect(jsonPath("$.valor").value(65));

        mvc.perform(logado(get(BASE + "/financeiro/historico")))
                .andExpect(jsonPath("$[0].entidade").value("agendamento"))
                .andExpect(jsonPath("$[0].antes").value("CONCLUIDO · R$ 50.00"))
                .andExpect(jsonPath("$[0].depois").value("CONCLUIDO · R$ 65"))
                .andExpect(jsonPath("$[1].antes").value("AGUARDANDO · R$ -"))
                .andExpect(content().string(not(containsString("Ana"))));
    }

    @Test
    void quarentaMinutosEntreUmAtendimentoEOutroDeQualquerCliente() throws Exception {
        long thor = novoPet();
        long tutor2 = criar("/tutores", """
                {"nome":"Bruno Lima","whatsapp":"(62) 98888-0000"}
                """).get("id").asLong();
        long luna = criar("/tutores/" + tutor2 + "/pets", """
                {"nome":"Luna","especie":"Gato"}
                """).get("id").asLong();
        String agendar = """
                {"petId":%d,"data":"2026-03-02","hora":"%s","tipo":"BANHO"}
                """;
        String perto = "Muito perto do atendimento das 10:00. Deixe pelo menos 40 minutos entre um atendimento e outro.";

        long idThor = criar("/agenda", agendar.formatted(thor, "10:00")).get("id").asLong();
        // outro cliente: mesmo horário, 30 min depois e 30 min antes são recusados
        for (String hora : new String[]{"10:00", "10:30", "09:30", "10:39"}) {
            mvc.perform(logado(post(BASE + "/agenda")).content(agendar.formatted(luna, hora)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.detail").value(perto));
        }
        // exatamente 40 min depois (e antes) pode
        long idLuna = criar("/agenda", agendar.formatted(luna, "10:40")).get("id").asLong();
        criar("/agenda", agendar.formatted(luna, "09:20"));
        // em outro dia, o mesmo horário pode
        criar("/agenda", agendar.replace("2026-03-02", "2026-03-03").formatted(luna, "10:00"));

        // cancelar libera o espaço; reativar depois que alguém ocupou é recusado
        mvc.perform(logado(patch(BASE + "/agenda/" + idLuna + "/status")).content("{\"status\":\"CANCELADO\"}"))
                .andExpect(status().isOk());
        mvc.perform(logado(patch(BASE + "/agenda/" + idThor + "/status")).content("{\"status\":\"CANCELADO\"}"))
                .andExpect(status().isOk());
        criar("/agenda", agendar.formatted(luna, "10:20"));
        mvc.perform(logado(patch(BASE + "/agenda/" + idThor + "/status")).content("{\"status\":\"CONFIRMADO\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Muito perto do atendimento das 10:20. Deixe pelo menos 40 minutos entre um atendimento e outro."));
    }

    @Test
    void dataForaDoPeriodoEhRecusada() throws Exception {
        mvc.perform(logado(post(BASE + "/financeiro/lancamentos")).content("""
                        {"data":"1900-01-05","categoriaId":%d,"valor":10,"recorrente":true}
                        """.formatted(categoria("FIXO", "Aluguel"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.dataValida").value("Use uma data entre 2000 e 2100"));
    }

    @Test
    void resumoDoMesComComparacaoECustoMedio() throws Exception {
        long petId = novoPet();
        lancar("2026-03-05", categoria("FIXO", "Aluguel"), "1000.00", false);
        lancar("2026-03-10", categoria("FIXO", "Energia"), "200.50", false);
        lancar("2026-03-12", categoria("VARIAVEL", "Shampoo e produtos de banho"), "99.50", false);
        lancar("2026-03-15", categoria("RECEITA", "Ração"), "50.00", false);
        lancar("2026-03-20", categoria("EVENTUAL", "Equipamentos"), "500.00", true); // secador novo; nunca repete
        lancar("2026-02-05", categoria("FIXO", "Aluguel"), "1000.00", false);
        agendamentoConcluido(petId, "2026-03-02", "09:00", "BANHO", null);   // preço padrão da migration: 50,00
        agendamentoConcluido(petId, "2026-03-03", "09:00", "TOSA", "70.00");
        criar("/agenda", """
                {"petId":%d,"data":"2026-03-04","hora":"09:00","tipo":"BANHO"}
                """.formatted(petId)); // não concluído: não conta

        mvc.perform(logado(get(BASE + "/financeiro/lancamentos").param("mes", "2026-03")))
                .andExpect(jsonPath("$[?(@.tipo == 'EVENTUAL')].recorrente", contains(false)));
        mvc.perform(logado(get(BASE + "/financeiro/resumo").param("mes", "2026-03")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.atual.mes").value("2026-03"))
                .andExpect(jsonPath("$.atual.receitaAtendimentos").value(120.00))
                .andExpect(jsonPath("$.atual.receitaAvulsa").value(50.00))
                .andExpect(jsonPath("$.atual.receita").value(170.00))
                .andExpect(jsonPath("$.atual.totalFixo").value(1200.50))
                .andExpect(jsonPath("$.atual.totalVariavel").value(99.50))
                .andExpect(jsonPath("$.atual.totalEventual").value(500.00))
                .andExpect(jsonPath("$.atual.custos").value(1800.00))
                .andExpect(jsonPath("$.atual.lucro").value(-1630.00))
                .andExpect(jsonPath("$.atual.atendimentos").value(2))
                // eventual fica fora: (1200,50 + 99,50) ÷ 2
                .andExpect(jsonPath("$.atual.custoMedioPorAtendimento").value(650.00))
                .andExpect(jsonPath("$.anterior.mes").value("2026-02"))
                .andExpect(jsonPath("$.anterior.totalFixo").value(1000.00))
                .andExpect(jsonPath("$.anterior.atendimentos").value(0))
                .andExpect(jsonPath("$.anterior.custoMedioPorAtendimento").value(nullValue()))
                .andExpect(jsonPath("$.ultimosMeses", hasSize(6)))
                .andExpect(jsonPath("$.ultimosMeses[0].mes").value("2025-10"))
                .andExpect(jsonPath("$.ultimosMeses[5].mes").value("2026-03"));
    }

    @Test
    void renomeiaCategoriaEExcluiSoSemLancamentos() throws Exception {
        long internet = criar("/financeiro/categorias", "{\"nome\":\"Internt\",\"tipo\":\"FIXO\"}").get("id").asLong();
        mvc.perform(logado(put(BASE + "/financeiro/categorias/" + internet)).content("{\"nome\":\"Internet\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Internet"))
                .andExpect(jsonPath("$.tipo").value("FIXO"));
        // nome que já existe no mesmo tipo
        mvc.perform(logado(put(BASE + "/financeiro/categorias/" + internet)).content("{\"nome\":\"aluguel\"}"))
                .andExpect(status().isConflict());

        // em uso: não exclui; o lançamento mostra o nome novo
        long id = lancar("2026-03-10", internet, "99.90", false);
        mvc.perform(logado(get(BASE + "/financeiro/lancamentos").param("mes", "2026-03")))
                .andExpect(jsonPath("$[0].categoriaNome").value("Internet"));
        mvc.perform(logado(delete(BASE + "/financeiro/categorias/" + internet)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(containsString("1 lançamento")));

        mvc.perform(logado(delete(BASE + "/financeiro/lancamentos/" + id))).andExpect(status().isNoContent());
        mvc.perform(logado(delete(BASE + "/financeiro/categorias/" + internet))).andExpect(status().isNoContent());
        mvc.perform(logado(get(BASE + "/financeiro/categorias"))).andExpect(jsonPath("$", hasSize(18)));
    }
}
