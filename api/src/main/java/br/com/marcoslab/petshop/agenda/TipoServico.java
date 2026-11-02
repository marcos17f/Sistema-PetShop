package br.com.marcoslab.petshop.agenda;

public enum TipoServico {
    BANHO("banho"), TOSA("tosa"), BANHO_E_TOSA("banho e tosa"), VACINA("vacina"), CONSULTA("consulta");

    private final String nome;

    TipoServico(String nome) {
        this.nome = nome;
    }

    /** Nome em minúsculas, usado nas mensagens de WhatsApp. */
    public String nome() {
        return nome;
    }

    public boolean incluiBanho() {
        return this == BANHO || this == BANHO_E_TOSA;
    }
}
