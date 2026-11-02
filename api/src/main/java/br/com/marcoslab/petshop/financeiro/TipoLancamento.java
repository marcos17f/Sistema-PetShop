package br.com.marcoslab.petshop.financeiro;

/** EVENTUAL = despesa fora do dia a dia (equipamento, conserto, reforma): entra no lucro, não no custo por atendimento. */
public enum TipoLancamento {
    FIXO, VARIAVEL, EVENTUAL, RECEITA;

    /** Despesa fixa ou variável pode repetir todo mês; eventual é pontual e receita não repete. */
    public boolean podeRepetir() {
        return this == FIXO || this == VARIAVEL;
    }
}
