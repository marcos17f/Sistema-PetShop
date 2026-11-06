package br.com.marcoslab.petshop.online;

import br.com.marcoslab.petshop.agenda.AgendaService;
import br.com.marcoslab.petshop.agenda.Agendamento;
import br.com.marcoslab.petshop.agenda.Canal;
import br.com.marcoslab.petshop.cliente.Pet;
import br.com.marcoslab.petshop.cliente.PetRepository;
import br.com.marcoslab.petshop.cliente.Tutor;
import br.com.marcoslab.petshop.cliente.TutorRepository;
import br.com.marcoslab.petshop.erro.Erros;
import br.com.marcoslab.petshop.loja.Loja;
import br.com.marcoslab.petshop.whatsapp.MensagemWhatsApp.Tipo;
import br.com.marcoslab.petshop.whatsapp.WhatsAppService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static br.com.marcoslab.petshop.cliente.ClienteDtos.vazioParaNull;

/**
 * Agendamento feito pelo próprio cliente na página /agendar. Usa as mesmas regras da agenda do painel.
 * LGPD/privacidade: nunca revela se o WhatsApp já é cliente nem devolve dados de cadastro; só associa por trás.
 */
@Service
public class AgendamentoOnlineService {

    public record Confirmacao(String data, String hora, String servico, String petNome, boolean buscaMoto, String mensagem) {
    }

    private static final DateTimeFormatter DIA = DateTimeFormatter.ofPattern("dd/MM");
    private static final Map<String, String> ORIGENS = Map.of("instagram", "Instagram", "google", "Google",
            "whatsapp", "WhatsApp", "indicacao", "Indicação");

    private final AgendaService agenda;
    private final TutorRepository tutores;
    private final PetRepository pets;
    private final WhatsAppService whatsapp;
    private final Clock clock;
    private final String templateConfirmacao;
    private final LimitePorEndereco limite;

    public AgendamentoOnlineService(AgendaService agenda, TutorRepository tutores, PetRepository pets, WhatsAppService whatsapp,
                                    Clock clock, @Value("${app.whatsapp.template-confirmacao:confirmacao_agendamento}") String templateConfirmacao,
                                    @Value("${app.online.limite-por-hora:5}") int limitePorHora) {
        this.agenda = agenda;
        this.tutores = tutores;
        this.pets = pets;
        this.whatsapp = whatsapp;
        this.clock = clock;
        this.templateConfirmacao = templateConfirmacao;
        this.limite = new LimitePorEndereco(limitePorHora, Duration.ofHours(1), clock);
    }

    @Transactional
    public Confirmacao agendar(Loja loja, PedidoOnline p, String ip) {
        if (p.site() != null && !p.site().isBlank()) {
            throw new Erros.Conflito("Não foi possível concluir o agendamento."); // campo invisível preenchido: robô
        }
        if (!limite.permitir(ip)) {
            throw new Erros.MuitasTentativas("Muitos agendamentos seguidos. Tente de novo mais tarde ou chame a loja no WhatsApp.");
        }
        Tutor tutor = clientePeloWhatsapp(loja, p);
        if (p.aceitePromocoes()) tutor.aceitarPromocoes(clock.instant());
        Pet pet = tutor.getPets().stream().filter(x -> x.getNome().equalsIgnoreCase(p.petNome().trim())).findFirst()
                .orElseGet(() -> pets.save(new Pet(tutor, p.petNome(), p.especie(), null, null, null, null)));

        Agendamento a = agenda.agendar(loja, pet, p.data(), p.hora(), p.tipo(), vazioParaNull(p.observacao()),
                p.buscaMoto(), vazioParaNull(p.enderecoBusca()), Canal.ONLINE);

        String servico = a.getTipo().nome();
        String detalhes = "%s do %s em %s às %s%s".formatted(servico, pet.getNome(), a.getData().format(DIA), a.getHora(),
                a.isBuscaMoto() ? " (com busca de moto)" : "");
        String texto = "Olá, %s! Aqui é da %s. Recebemos seu agendamento: %s. Confirmamos pelo WhatsApp em breve.".formatted(
                primeiroNome(p.nome()), loja.getNome(), detalhes);
        whatsapp.enviarTemplate(loja, tutor.getWhatsapp(), Tipo.CONFIRMACAO, texto, templateConfirmacao,
                List.of(loja.getNome(), detalhes), "CONFIRMACAO:" + a.getId());
        return new Confirmacao(a.getData().format(DIA), a.getHora().toString(), servico, pet.getNome(), a.isBuscaMoto(), texto);
    }

    /** Reaproveita o cliente do mesmo WhatsApp; senão cadastra com a origem (Instagram, Google...). Não sobrescreve dados. */
    private Tutor clientePeloWhatsapp(Loja loja, PedidoOnline p) {
        String digitos = p.whatsapp().replaceAll("\\D", "");
        String local = digitos.startsWith("55") && digitos.length() >= 12 ? digitos.substring(2) : digitos;
        return tutores.porWhatsapp(loja.getId(), local, "55" + local).stream().findFirst().orElseGet(() -> {
            String origem = ORIGENS.getOrDefault(p.origem() == null ? "" : p.origem().toLowerCase(Locale.ROOT), "Site");
            return tutores.save(new Tutor(loja, p.nome(), local, null, null, origem, clock.instant()));
        });
    }

    private static String primeiroNome(String nome) {
        return nome.trim().split("\\s+")[0];
    }
}
