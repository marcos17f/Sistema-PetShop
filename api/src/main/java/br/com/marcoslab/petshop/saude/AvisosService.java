package br.com.marcoslab.petshop.saude;

import br.com.marcoslab.petshop.config.LimiteDeLogin;
import br.com.marcoslab.petshop.loja.Loja;
import br.com.marcoslab.petshop.whatsapp.MensagemRepository;
import br.com.marcoslab.petshop.whatsapp.MensagemWhatsApp.Status;
import br.com.marcoslab.petshop.whatsapp.WhatsAppService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.lang.management.MemoryUsage;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Avisos para a loja trabalhar tranquila: avisa ANTES de algo parar (limite de plano, espaço, memória, falhas, segurança),
 * sempre dizendo o que fazer. Mostrados no topo do painel e na tela Saúde do sistema.
 */
@Service
public class AvisosService {

    public enum Nivel { CRITICO, ATENCAO, INFO }

    public record Aviso(Nivel nivel, String area, String titulo, String detalhe, String acao) {
    }

    private final WhatsAppService whatsapp;
    private final MensagemRepository mensagens;
    private final LimiteDeLogin limiteDeLogin;
    private final ContadorDeErros erros;
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final int limiteMensagensMes;
    private final int limiteBancoMb;
    private final boolean senhaSemCriptografia;

    public AvisosService(WhatsAppService whatsapp, MensagemRepository mensagens, LimiteDeLogin limiteDeLogin, ContadorDeErros erros,
                         JdbcTemplate jdbc, Clock clock,
                         @Value("${app.limites.whatsapp-mensagens-mes:1000}") int limiteMensagensMes,
                         @Value("${app.limites.banco-mb:1000}") int limiteBancoMb,
                         @Value("${app.painel.senha}") String senhaPainel) {
        this.whatsapp = whatsapp;
        this.mensagens = mensagens;
        this.limiteDeLogin = limiteDeLogin;
        this.erros = erros;
        this.jdbc = jdbc;
        this.clock = clock;
        this.limiteMensagensMes = limiteMensagensMes;
        this.limiteBancoMb = limiteBancoMb;
        this.senhaSemCriptografia = senhaPainel.startsWith("{noop}");
    }

    @Transactional(readOnly = true)
    public List<Aviso> avisos(Loja loja) {
        List<Aviso> lista = new ArrayList<>();
        whatsapp(loja, lista);
        banco(lista);
        servidor(lista);
        seguranca(lista);
        lista.sort(Comparator.comparing(Aviso::nivel));
        return lista;
    }

    private void whatsapp(Loja loja, List<Aviso> lista) {
        if (!whatsapp.oficial() && loja.isLembretesAutomaticos()) {
            lista.add(new Aviso(Nivel.ATENCAO, "WhatsApp", "Lembretes automáticos ligados, mas nada está saindo",
                    "O WhatsApp está no modo simulado (sem a API oficial). Os lembretes ficam só registrados.",
                    "Mande por clique na tela WhatsApp, ou configure a WhatsApp Cloud API no servidor (README, seção 4)."));
        }
        if (!whatsapp.oficial()) return;
        Instant inicioMes = YearMonth.now(clock).atDay(1).atStartOfDay(clock.getZone()).toInstant();
        long enviadas = mensagens.contar(loja.getId(), Status.ENVIADA, inicioMes);
        int pct = (int) (enviadas * 100 / Math.max(1, limiteMensagensMes));
        if (pct >= 100) {
            lista.add(new Aviso(Nivel.CRITICO, "WhatsApp", "Limite de mensagens do mês atingido",
                    "%d mensagens enviadas este mês (limite configurado: %d).".formatted(enviadas, limiteMensagensMes),
                    "Hora de contratar um plano maior ou ajustar LIMITE_WHATSAPP_MES ao seu plano. Mensagens extras podem ser cobradas."));
        } else if (pct >= 80) {
            lista.add(new Aviso(Nivel.ATENCAO, "WhatsApp", "Mensagens do mês chegando ao limite",
                    "%d de %d mensagens usadas (%d%%).".formatted(enviadas, limiteMensagensMes, pct),
                    "Avalie um plano maior antes do fim do mês, ou desligue envios que não são essenciais."));
        }
        Instant ontem = clock.instant().minus(Duration.ofHours(24));
        if (mensagens.falhasDeAcesso(loja.getId(), ontem) > 0) {
            lista.add(new Aviso(Nivel.CRITICO, "WhatsApp", "A Meta recusou o acesso (token vencido ou inválido)",
                    "Mensagens não estão saindo desde a última falha.",
                    "Gere um token permanente novo na Meta e troque WHATSAPP_TOKEN no servidor."));
        } else {
            long falhas = mensagens.contar(loja.getId(), Status.FALHOU, ontem);
            if (falhas > 0) {
                lista.add(new Aviso(Nivel.ATENCAO, "WhatsApp", falhas + " mensagem(ns) com falha nas últimas 24 h",
                        "Veja o motivo de cada uma na tela WhatsApp (ex.: modelo não aprovado, número inválido).",
                        "Corrija o motivo e use “Abrir no WhatsApp” para mandar as que falharam."));
            }
        }
    }

    private void banco(List<Aviso> lista) {
        Long bytes;
        try {
            bytes = jdbc.queryForObject("select pg_database_size(current_database())", Long.class);
        } catch (RuntimeException e) {
            return; // não é PostgreSQL (ex.: testes com H2)
        }
        if (bytes == null) return;
        long mb = bytes / (1024 * 1024);
        int pct = (int) (mb * 100 / Math.max(1, limiteBancoMb));
        if (pct >= 80) {
            lista.add(new Aviso(pct >= 95 ? Nivel.CRITICO : Nivel.ATENCAO, "Banco de dados", "Espaço do banco chegando ao limite",
                    "%d MB de %d MB (%d%%).".formatted(mb, limiteBancoMb, pct),
                    "Aumente o disco/plano da hospedagem (e ajuste LIMITE_BANCO_MB) antes de lotar."));
        }
    }

    private void servidor(List<Aviso> lista) {
        int pct = memoriaAposLimpeza();
        if (pct >= 85) {
            lista.add(new Aviso(Nivel.ATENCAO, "Servidor", "Memória do servidor quase cheia",
                    "A API está usando %d%% da memória disponível.".formatted(pct),
                    "Se continuar, aumente a memória do servidor (plano da hospedagem) para o sistema não ficar lento."));
        }
        int n = erros.ultimaHora();
        if (n >= 5) {
            lista.add(new Aviso(n >= 20 ? Nivel.CRITICO : Nivel.ATENCAO, "Servidor", n + " erros do sistema na última hora",
                    "Algumas ações não estão funcionando.", "Veja os logs da API no servidor e avise o suporte técnico."));
        }
    }

    /**
     * % da memória que continua ocupada logo depois da coleta de lixo (geração antiga). O uso instantâneo sobe e desce o
     * tempo todo e daria alarme falso; o que sobra após a limpeza mostra pressão de verdade.
     */
    private static int memoriaAposLimpeza() {
        int maior = 0;
        for (MemoryPoolMXBean pool : ManagementFactory.getMemoryPoolMXBeans()) {
            MemoryUsage apos = pool.getType() == MemoryType.HEAP && pool.isCollectionUsageThresholdSupported()
                    ? pool.getCollectionUsage() : null;
            if (apos != null && apos.getMax() > 0) maior = Math.max(maior, (int) (apos.getUsed() * 100 / apos.getMax()));
        }
        return maior;
    }

    private void seguranca(List<Aviso> lista) {
        if (senhaSemCriptografia) {
            lista.add(new Aviso(Nivel.ATENCAO, "Segurança", "Senha do painel sem criptografia",
                    "A senha está em texto puro na configuração do servidor (normal só no computador de desenvolvimento).",
                    "Antes de publicar, gere a senha com bcrypt e coloque em PAINEL_SENHA (README, seção 6)."));
        }
        int bloqueios = limiteDeLogin.bloqueiosUltimas24h();
        if (bloqueios > 0) {
            lista.add(new Aviso(Nivel.INFO, "Segurança", "Tentativas de senha erradas bloqueadas",
                    bloqueios + " endereço(s) bloqueado(s) nas últimas 24 h por errar a senha muitas vezes.",
                    "Se não foi alguém da loja, troque a senha do painel por uma mais forte."));
        }
    }
}
