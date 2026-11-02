/**
 * Exporta o financeiro do mês para Excel (.xlsx), para o Cleiton e o contador.
 * Gera no navegador, então funciona igual com a API e no modo demonstração.
 * A biblioteca é carregada só quando o botão é clicado.
 *
 * Abas: Painel (dashboard), Receitas, Custos fixos, Custos variáveis, Despesas eventuais.
 * O Painel usa fórmulas ligadas às outras abas: se o contador corrigir um valor, o painel recalcula.
 * A biblioteca não grava gráficos nativos; as barras do painel são feitas com REPT("█"), que funciona
 * no Excel, LibreOffice e Google Planilhas.
 *
 * Segurança/LGPD: textos digitados (descrição) vão como texto puro, nunca como fórmula,
 * e a planilha não leva nome nem contato do tutor, só o serviço e o pet.
 */
import { formatar, hoje, nomeMes } from "./datas";
import { loja } from "./loja";
import { NOMES_SERVICO, type Agendamento, type Lancamento, type Resumo } from "./tipos";

const MOEDA = '"R$" #,##0.00;[Red]-"R$" #,##0.00';
const DIFERENCA = '+"R$" #,##0.00;[Red]-"R$" #,##0.00;"R$" 0,00';
const PORCENTO = "0%";

// mesmas cores do painel
const VERDE_ESCURO = "#0e3b35";
const VERDE = "#0b8f78";
const MENTA = "#e6f0ee";
const LINHA = "#d5dfdc";
const VERMELHO = "#c2410c";
const CINZA = "#4c5b58";

type Linha = [string, string, string, number];

export async function exportarMes(resumo: Resumo, lancamentos: Lancamento[], atendimentos: Agendamento[]) {
  const { default: writeXlsxFile } = await import("write-excel-file/browser");
  const { atual, anterior, ultimosMeses } = resumo;

  /* ---------- estilos ---------- */
  const titulo = (value: string, columnSpan: number) => ({
    value, columnSpan, fontWeight: "bold" as const, fontSize: 16, textColor: "#ffffff", backgroundColor: VERDE_ESCURO,
  });
  const secao = (value: string, columnSpan: number) => ({
    value, columnSpan, fontWeight: "bold" as const, fontSize: 12, textColor: VERDE_ESCURO, bottomBorderStyle: "medium" as const, bottomBorderColor: VERDE,
  });
  const cab = (value: string, align: "left" | "right" = "left") => ({
    value, align, fontWeight: "bold" as const, textColor: "#ffffff", backgroundColor: VERDE,
  });
  const texto = (value: string) => ({ value, type: String });
  const nota = (value: string, columnSpan: number) => ({ value, columnSpan, fontStyle: "italic" as const, textColor: CINZA });
  // no XML do .xlsx a fórmula vai sem o "=" inicial (a biblioteca grava o texto como recebe)
  const formula = (value: string, format = MOEDA, extra: object = {}) =>
    ({ value: value.replace(/^=/, ""), type: "Formula" as const, format, ...extra });
  const dinheiro = (value: number, extra: object = {}) => ({ value, format: MOEDA, ...extra });
  const negritoTopo = { fontWeight: "bold" as const, topBorderStyle: "thin" as const, topBorderColor: VERDE_ESCURO };

  /* ---------- abas de lançamentos ---------- */

  /** Aba Data | Categoria | Descrição | Valor, com cabeçalho fixo e linha de total. */
  function aba(nome: string, linhas: Linha[]) {
    const fim = linhas.length + 1;
    return {
      sheet: nome,
      columns: [{ width: 12 }, { width: 30 }, { width: 44 }, { width: 16 }],
      stickyRowsCount: 1,
      dateFormat: "dd/mm/yyyy",
      data: [
        [cab("Data"), cab("Categoria"), cab("Descrição"), cab("Valor", "right")],
        ...linhas.map(([d, c, desc, v]) => [new Date(`${d}T12:00:00`), texto(c), texto(desc), dinheiro(v)]),
        linhas.length === 0
          ? [nota("Nenhum lançamento neste mês.", 3), null, null, dinheiro(0)]
          : [{ value: "", ...negritoTopo }, { value: "", ...negritoTopo }, { value: "Total", ...negritoTopo },
            formula(`=SUM(D2:D${fim})`, MOEDA, negritoTopo)],
      ],
      total: `'${nome}'!D${(linhas.length === 0 ? 2 : fim + 1)}`,
    };
  }

  const de = (tipo: Lancamento["tipo"]) =>
    lancamentos.filter((l) => l.tipo === tipo).map((l): Linha => [l.data, l.categoriaNome, l.descricao, l.valor]);
  const receitas = aba("Receitas", [
    ...atendimentos.map((a): Linha => [a.data, `Atendimento: ${NOMES_SERVICO[a.tipo]}`, `Pet: ${a.petNome}`, a.valor ?? 0]),
    ...de("RECEITA"),
  ]);
  const fixos = aba("Custos fixos", de("FIXO"));
  const variaveis = aba("Custos variáveis", de("VARIAVEL"));
  const eventuais = aba("Despesas eventuais", de("EVENTUAL"));

  /* ---------- Painel (dashboard) ---------- */
  // Mapa de linhas do Painel (as fórmulas abaixo dependem dele):
  //  1 título · 2 subtítulo · 4 seção · 5 cabeçalho
  //  6 receita · 7 fixos · 8 variáveis · 9 eventuais · 10 despesas · 11 lucro · 12 atendimentos · 13 custo médio
  //  15 seção · 16 cabeçalho · 17-19 fixos/variáveis/eventuais
  //  21 seção · 22 cabeçalho · 23-28 últimos 6 meses · 29 nota
  const ant = nomeMes(anterior.mes, true);
  const diff = (r: number) => formula(`=IFERROR(B${r}-C${r},"-")`, DIFERENCA);
  const linhaKpi = (rotulo: string, r: number, esteMes: object, antes: number | string, format = MOEDA, extra: object = {}) => [
    { value: rotulo, ...extra }, { ...esteMes, ...extra }, { value: antes, format, ...extra }, { ...diff(r), ...extra },
  ];

  const barra = (ref: string, maximo: string) => formula(`=REPT("█",ROUND(ABS(${ref})/MAX(${maximo},0.01)*20,0))`, "@", { textColor: VERDE });

  const meses = ultimosMeses.map((m, i) => {
    const r = 23 + i;
    return [
      texto(nomeMes(m.mes)),
      dinheiro(m.receita),
      dinheiro(m.custos),
      dinheiro(m.lucro, { fontWeight: "bold" }),
      barra(`D${r}`, "MAX(ABS(MAX($D$23:$D$28)),ABS(MIN($D$23:$D$28)))"),
    ];
  });

  const painel = {
    sheet: "Painel",
    columns: [{ width: 30 }, { width: 18 }, { width: 18 }, { width: 18 }, { width: 26 }],
    showGridLines: false,
    data: [
      [titulo(`${loja.nome} · Financeiro de ${nomeMes(atual.mes).toLowerCase()}`, 5), null, null, null, null],
      [nota(`Gerado em ${formatar(hoje(), true)} · valores em reais · as outras abas trazem cada lançamento`, 5), null, null, null, null],
      [null],
      [secao("Resumo do mês", 4), null, null, null],
      [cab("Indicador"), cab(nomeMes(atual.mes, true), "right"), cab(ant, "right"), cab("Diferença", "right")],
      linhaKpi("Receita", 6, formula(`=${receitas.total}`), anterior.receita),
      linhaKpi("Custos fixos", 7, formula(`=${fixos.total}`), anterior.totalFixo),
      linhaKpi("Custos variáveis", 8, formula(`=${variaveis.total}`), anterior.totalVariavel),
      linhaKpi("Despesas eventuais", 9, formula(`=${eventuais.total}`), anterior.totalEventual),
      linhaKpi("Despesas totais", 10, formula("=B7+B8+B9"), anterior.custos, MOEDA, negritoTopo),
      linhaKpi("LUCRO DO MÊS", 11, formula("=B6-B10"), anterior.lucro, MOEDA, { fontWeight: "bold", fontSize: 13 }),
      linhaKpi("Atendimentos concluídos", 12, { value: atual.atendimentos, format: "0" }, anterior.atendimentos, "0"),
      // eventuais ficam fora do custo por atendimento
      linhaKpi("Custo médio por atendimento", 13, formula('=IF(B12>0,(B7+B8)/B12,"-")'), anterior.custoMedioPorAtendimento ?? "-"),
      [null],
      [secao("Para onde foi o dinheiro", 4), null, null, null],
      [cab("Tipo de despesa"), cab("Valor", "right"), cab("% do total", "right"), cab("")],
      ...[["Custos fixos", 7], ["Custos variáveis", 8], ["Despesas eventuais", 9]].map(([rotulo, r], i) => [
        texto(String(rotulo)),
        formula(`=B${r}`),
        formula(`=IF($B$10>0,B${17 + i}/$B$10,0)`, PORCENTO),
        barra(`C${17 + i}`, "1"),
      ]),
      [null],
      [secao("Últimos 6 meses", 5), null, null, null, null],
      [cab("Mês"), cab("Receita", "right"), cab("Despesas", "right"), cab("Lucro", "right"), cab("Lucro (gráfico)")],
      ...meses,
      [nota("Barras verdes = lucro; vermelhas = prejuízo. Meses anteriores: valores do sistema no dia da exportação.", 5), null, null, null, null],
    ],
    conditionalFormatting: [
      // lucro do mês: fundo verde se positivo, vermelho se negativo
      { cellRange: { from: { row: 11, column: 2 }, to: { row: 11, column: 2 } }, condition: { operator: ">=" as const, value: 0 }, style: { backgroundColor: MENTA, textColor: VERDE_ESCURO } },
      { cellRange: { from: { row: 11, column: 2 }, to: { row: 11, column: 2 } }, condition: { operator: "<" as const, value: 0 }, style: { backgroundColor: "#fbdad3", textColor: VERMELHO } },
      // barras dos meses com prejuízo em vermelho
      { cellRange: { from: { row: 23, column: 5 }, to: { row: 28, column: 5 } }, condition: { formula: "=$D23<0" }, style: { textColor: VERMELHO } },
      // linhas zebradas no resumo
      { cellRange: { from: { row: 6, column: 1 }, to: { row: 13, column: 4 } }, condition: { formula: "=MOD(ROW(),2)=0" }, style: { backgroundColor: "#f3f6f5", borderColor: LINHA } },
    ],
  };

  const semTotal = ({ total: _, ...s }: ReturnType<typeof aba>) => s; // eslint-disable-line @typescript-eslint/no-unused-vars
  await writeXlsxFile([painel, semTotal(receitas), semTotal(fixos), semTotal(variaveis), semTotal(eventuais)])
    .toFile(`financeiro-${atual.mes}.xlsx`);
}
