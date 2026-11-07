package br.com.marcoslab.petshop;

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
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HexFormat;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Agendamento online pelo cliente (sem login) e mensagens recebidas pelo webhook do WhatsApp. */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@TestPropertySource(properties = {"app.whatsapp.app-secret=segredo-do-app", "app.whatsapp.verify-token=verifica-123",
        "app.site-url=https://petshop.test"})
class AgendamentoOnlineApiTest {

    private static final String PUB = "/api/publico/lojas/petshop-cleiton";
    private static final String BASE = "/api/lojas/petshop-cleiton";

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    JdbcTemplate jdbc;

    /** Próximo dia útil a pelo menos 2 dias de hoje (loja aberta 08:00–18:00, intervalo 40 min). */
    private static LocalDate diaUtil() {
        LocalDate d = LocalDate.now(ZoneId.of("America/Sao_Paulo")).plusDays(2);
        while (d.getDayOfWeek() == DayOfWeek.SATURDAY || d.getDayOfWeek() == DayOfWeek.SUNDAY) d = d.plusDays(1);
        return d;
    }

    private static RequestPostProcessor ip(String ip) {
        return r -> { r.setRemoteAddr(ip); return r; };
    }

    private MockHttpServletRequestBuilder publico(LocalDate dia, String hora, String extra) {
        return post(PUB + "/agendamentos").with(ip("200.1.1.1")).contentType(MediaType.APPLICATION_JSON).content("""
                {"nome":"Ana Paula Souza","whatsapp":"(62) 99999-1234","petNome":"Thor","especie":"Cão","tipo":"BANHO",
                 "data":"%s","hora":"%s","observacao":"","buscaMoto":false,"enderecoBusca":"","aceiteDados":true,
                 "aceitePromocoes":false,"origem":"instagram","site":""%s}
                """.formatted(dia, hora, extra));
    }

    private int contar(String tabela) {
        return jdbc.queryForObject("select count(*) from " + tabela, Integer.class);
    }

    @Test
    void mostraServicosEHorariosLivres() throws Exception {
        LocalDate dia = diaUtil();
        mvc.perform(get(PUB + "/servicos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.tipo == 'BANHO')].preco", contains(50.0)))
                .andExpect(jsonPath("$[?(@.tipo == 'BANHO')].nome", contains("Banho")));
        mvc.perform(get(PUB + "/horarios").param("data", dia.toString()))
                .andExpect(jsonPath("$", hasSize(15)))
                .andExpect(jsonPath("$[0]").value("08:00"))
                .andExpect(jsonPath("$[14]").value("17:20"));
        // domingo fechado; passado e longe demais não têm horário
        LocalDate domingo = dia.with(java.time.temporal.TemporalAdjusters.next(DayOfWeek.SUNDAY));
        mvc.perform(get(PUB + "/horarios").param("data", domingo.toString())).andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(get(PUB + "/horarios").param("data", dia.minusYears(1).toString())).andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(get(PUB + "/horarios").param("data", dia.plusDays(90).toString())).andExpect(jsonPath("$", hasSize(0)));

        // um atendimento às 09:00 marcado no painel tira 08:40 e 09:20 (intervalo de 40 min)
        long tutor = json.readTree(mvc.perform(post(BASE + "/tutores").with(httpBasic("loja", "senha-teste"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"nome\":\"Bruno\",\"whatsapp\":\"(62) 98888-0000\"}"))
                .andReturn().getResponse().getContentAsString()).get("id").asLong();
        long pet = json.readTree(mvc.perform(post(BASE + "/tutores/" + tutor + "/pets").with(httpBasic("loja", "senha-teste"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"nome\":\"Luna\",\"especie\":\"Gato\"}"))
                .andReturn().getResponse().getContentAsString()).get("id").asLong();
        mvc.perform(post(BASE + "/agenda").with(httpBasic("loja", "senha-teste")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"petId\":%d,\"data\":\"%s\",\"hora\":\"09:00\",\"tipo\":\"TOSA\"}".formatted(pet, dia)))
                .andExpect(status().isCreated());
        mvc.perform(get(PUB + "/horarios").param("data", dia.toString()))
                .andExpect(jsonPath("$", not(hasItem("08:40"))))
                .andExpect(jsonPath("$", not(hasItem("09:20"))))
                .andExpect(jsonPath("$", hasItems("08:00", "10:00")));
    }

    @Test
    void clienteAgendaSozinhoComBuscaDeMotoEConfirmacaoNoWhatsapp() throws Exception {
        LocalDate dia = diaUtil();
        mvc.perform(publico(dia, "10:00", "").content("""
                        {"nome":"Ana Paula Souza","whatsapp":"(62) 99999-1234","petNome":"Thor","especie":"Cão","tipo":"BANHO",
                         "data":"%s","hora":"10:00","observacao":"Tem medo de secador","buscaMoto":true,
                         "enderecoBusca":"Rua 10, 200 - Setor Oeste","aceiteDados":true,"aceitePromocoes":true,"origem":"instagram","site":""}
                        """.formatted(dia)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.hora").value("10:00"))
                .andExpect(jsonPath("$.buscaMoto").value(true))
                .andExpect(jsonPath("$.mensagem").value(containsString("Olá, Ana! Aqui é da A Arca Pet. Recebemos seu agendamento: banho do Thor")));

        // no painel: aguardando, online, com busca de moto
        mvc.perform(get(BASE + "/agenda").param("data", dia.toString()).with(httpBasic("loja", "senha-teste")))
                .andExpect(jsonPath("$[0].status").value("AGUARDANDO"))
                .andExpect(jsonPath("$[0].canal").value("ONLINE"))
                .andExpect(jsonPath("$[0].enderecoBusca").value("Rua 10, 200 - Setor Oeste"))
                .andExpect(jsonPath("$[0].tutorNome").value("Ana Paula Souza"));
        // cliente novo com origem e aceite de promoções registrado (LGPD)
        assertEquals("Instagram", jdbc.queryForObject("select origem from tutor", String.class));
        assertEquals(1, jdbc.queryForObject("select count(*) from tutor where consentimento_marketing_em is not null", Integer.class));
        // confirmação no WhatsApp (simulada sem a API oficial)
        mvc.perform(get(BASE + "/whatsapp/mensagens").with(httpBasic("loja", "senha-teste")))
                .andExpect(jsonPath("$[0].tipo").value("CONFIRMACAO"))
                .andExpect(jsonPath("$[0].para").value("5562999991234"));
        // o horário saiu da lista
        mvc.perform(get(PUB + "/horarios").param("data", dia.toString())).andExpect(jsonPath("$", not(hasItem("10:00"))));
    }

    @Test
    void clienteDaCasaNaoDuplicaENaoRevelaDados() throws Exception {
        LocalDate dia = diaUtil();
        mvc.perform(publico(dia, "08:00", "")).andExpect(status().isCreated());
        // mesmo WhatsApp com 55 e pet com letras diferentes: reaproveita cliente e pet
        mvc.perform(publico(dia, "11:20", "").content("""
                        {"nome":"Outro Nome","whatsapp":"+55 62 99999-1234","petNome":"THOR","especie":"Cão","tipo":"TOSA",
                         "data":"%s","hora":"11:20","aceiteDados":true,"site":""}
                        """.formatted(dia)))
                .andExpect(status().isCreated())
                // a resposta não traz nada do cadastro existente
                .andExpect(jsonPath("$.nome").doesNotExist())
                .andExpect(jsonPath("$.whatsapp").doesNotExist());
        assertEquals(1, contar("tutor"));
        assertEquals(1, contar("pet"));
        assertEquals("Ana Paula Souza", jdbc.queryForObject("select nome from tutor", String.class)); // não sobrescreve
        assertEquals(2, contar("agendamento"));
    }

    @Test
    void recusaHorarioOcupadoForaDaGradeRoboEDadosIncompletos() throws Exception {
        LocalDate dia = diaUtil();
        mvc.perform(publico(dia, "08:00", "")).andExpect(status().isCreated());
        mvc.perform(publico(dia, "08:00", ""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Esse horário não está mais disponível. Escolha outro."));
        mvc.perform(publico(dia, "08:10", "")).andExpect(status().isConflict()); // fora da grade
        mvc.perform(publico(LocalDate.now(ZoneId.of("America/Sao_Paulo")).minusDays(3), "10:00", "")).andExpect(status().isConflict());
        mvc.perform(publico(dia, "13:20", "").content("""
                        {"nome":"Ana","whatsapp":"(62) 99999-1234","petNome":"Thor","especie":"Cão","tipo":"BANHO",
                         "data":"%s","hora":"13:20","buscaMoto":true,"enderecoBusca":"","aceiteDados":false,"site":""}
                        """.formatted(dia)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.enderecoDaBusca").value("Informe o endereço para a busca de moto"))
                .andExpect(jsonPath("$.campos.aceiteDados").value("É preciso aceitar o uso dos dados para agendar"));
        // campo invisível preenchido = robô
        mvc.perform(publico(dia, "14:00", "").content(publicoCorpo(dia, "14:00").replace("\"site\":\"\"", "\"site\":\"http://spam\"")))
                .andExpect(status().isConflict());
        assertEquals(1, contar("agendamento"));
    }

    private String publicoCorpo(LocalDate dia, String hora) {
        return """
                {"nome":"Ana","whatsapp":"(62) 99999-1234","petNome":"Thor","especie":"Cão","tipo":"BANHO",
                 "data":"%s","hora":"%s","aceiteDados":true,"site":""}
                """.formatted(dia, hora);
    }

    @Test
    void limitaAgendamentosSeguidosDoMesmoEndereco() throws Exception {
        LocalDate dia = diaUtil();
        String[] horas = {"08:00", "08:40", "09:20", "10:00", "10:40", "11:20"};
        for (int i = 0; i < 5; i++) mvc.perform(publico(dia, horas[i], "")).andExpect(status().isCreated());
        mvc.perform(publico(dia, horas[5], ""))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.detail").value(containsString("Muitos agendamentos seguidos")));
    }

    private static String assinar(String corpo, String segredo) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(segredo.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return "sha256=" + HexFormat.of().formatHex(mac.doFinal(corpo.getBytes(StandardCharsets.UTF_8)));
    }

    private static final String MENSAGEM = """
            {"object":"whatsapp_business_account","entry":[{"changes":[{"value":{
              "contacts":[{"profile":{"name":"Carla Lima"},"wa_id":"5562977776666"}],
              "messages":[{"from":"5562977776666","type":"text","text":{"body":"Oi, vi vocês no Instagram, tem horário amanhã?"}}]
            }}]}]}
            """;

    @Test
    void webhookVerificaAssinaturaCadastraConversaNovaERespondeUmaVez() throws Exception {
        // verificação de cadastro do webhook pela Meta
        mvc.perform(get("/api/publico/whatsapp/webhook").param("hub.mode", "subscribe")
                        .param("hub.verify_token", "verifica-123").param("hub.challenge", "desafio-42"))
                .andExpect(status().isOk()).andExpect(content().string("desafio-42"));
        mvc.perform(get("/api/publico/whatsapp/webhook").param("hub.mode", "subscribe")
                        .param("hub.verify_token", "errado").param("hub.challenge", "x"))
                .andExpect(status().isForbidden());

        // assinatura errada ou ausente: recusado, nada é gravado
        mvc.perform(post("/api/publico/whatsapp/webhook").contentType(MediaType.APPLICATION_JSON).content(MENSAGEM)
                        .header("X-Hub-Signature-256", assinar(MENSAGEM, "segredo-falso")))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/publico/whatsapp/webhook").contentType(MediaType.APPLICATION_JSON).content(MENSAGEM))
                .andExpect(status().isUnauthorized());
        assertEquals(0, contar("tutor"));

        // resposta rápida ligada
        mvc.perform(put(BASE + "/whatsapp").with(httpBasic("loja", "senha-teste")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"lembretesAutomaticos\":false,\"respostaRapida\":true,\"textoRespostaRapida\":\"\"}"));

        for (int i = 0; i < 2; i++) { // a Meta pode reenviar: não duplica cliente nem resposta
            mvc.perform(post("/api/publico/whatsapp/webhook").contentType(MediaType.APPLICATION_JSON).content(MENSAGEM)
                            .header("X-Hub-Signature-256", assinar(MENSAGEM, "segredo-do-app")))
                    .andExpect(status().isOk());
        }
        assertEquals(1, contar("tutor"));
        assertEquals("Carla Lima", jdbc.queryForObject("select nome from tutor", String.class));
        assertEquals("62977776666", jdbc.queryForObject("select whatsapp from tutor", String.class));
        assertEquals("Instagram", jdbc.queryForObject("select origem from tutor", String.class));
        mvc.perform(get(BASE + "/whatsapp/mensagens").with(httpBasic("loja", "senha-teste")))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].tipo").value("RESPOSTA_RAPIDA"))
                .andExpect(jsonPath("$[0].texto").value(containsString("https://petshop.test/agendar?origem=whatsapp")));
    }

    @Test
    void corpoGiganteERecusadoAntesDeLer() throws Exception {
        byte[] grande = new byte[2 * 1024 * 1024];
        java.util.Arrays.fill(grande, (byte) 'a');
        mvc.perform(post("/api/publico/lojas/petshop-cleiton/agendamentos").contentType(MediaType.APPLICATION_JSON).content(grande))
                .andExpect(status().isPayloadTooLarge());
        mvc.perform(post("/api/publico/whatsapp/webhook").contentType(MediaType.APPLICATION_JSON).content(grande))
                .andExpect(status().isPayloadTooLarge());
    }
}
