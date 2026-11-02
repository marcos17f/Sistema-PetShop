package br.com.marcoslab.petshop.lembrete;

/**
 * Envio automático de mensagem. A implementação padrão só registra no log (o envio é por clique no painel).
 * Para automatizar, crie uma implementação com a WhatsApp Cloud API (mensagem de template "utility")
 * e marque-a com @Primary. Não use bibliotecas não oficiais: o número pode ser banido.
 */
public interface NotificadorWhatsApp {

    /** @return true se a mensagem foi aceita pelo provedor */
    boolean enviar(String whatsapp, String mensagem);
}
