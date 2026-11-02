import { nomeMes } from "@/lib/datas";
import { reais, type ResumoMes } from "@/lib/tipos";

/* Cores validadas para daltonismo (fixo x variável); lucro negativo em tom de alerta. */
const COR_FIXO = "#0b8f78";
const COR_VARIAVEL = "#c97f1f";
const COR_PREJUIZO = "#c2410c";

const L = 320; // largura do viewBox
const A = 170; // altura do viewBox
const BASE_Y = 140; // linha de base (zero)
const TOPO = 14;

/** Barra com a ponta de dado arredondada (4px) e a base reta na linha de zero. */
function barra(x: number, largura: number, y0: number, y1: number) {
  const r = Math.min(4, largura / 2, Math.abs(y1 - y0));
  if (Math.abs(y1 - y0) < 0.5) return "";
  const s = y1 < y0 ? 1 : -1; // 1 = para cima
  return `M${x},${y0} V${y1 + s * r} Q${x},${y1} ${x + r},${y1} H${x + largura - r} Q${x + largura},${y1} ${x + largura},${y1 + s * r} V${y0} Z`;
}

function compacto(v: number) {
  const abs = Math.abs(v);
  const txt = abs >= 1000 ? `${(abs / 1000).toLocaleString("pt-BR", { maximumFractionDigits: 1 })} mil` : abs.toFixed(0);
  return `${v < 0 ? "−" : ""}R$ ${txt}`;
}

/** Fixo x variável e lucro dos últimos 6 meses, com tabela para quem não enxerga as cores. */
export function Graficos({ meses }: { meses: ResumoMes[] }) {
  const passo = L / meses.length;

  const maxCusto = Math.max(1, ...meses.map((m) => Math.max(m.totalFixo, m.totalVariavel)));
  const yCusto = (v: number) => BASE_Y - (v / maxCusto) * (BASE_Y - TOPO);

  const maxLucro = Math.max(0, ...meses.map((m) => m.lucro));
  const minLucro = Math.min(0, ...meses.map((m) => m.lucro));
  const faixa = Math.max(1, maxLucro - minLucro);
  const zero = TOPO + (maxLucro / faixa) * (BASE_Y - TOPO);
  const yLucro = (v: number) => zero - (v / faixa) * (BASE_Y - TOPO);

  const rotulos = meses.map((m, i) => (
    <text key={m.mes} x={i * passo + passo / 2} y={A - 8} textAnchor="middle" className="fill-muted text-[11px]">
      {nomeMes(m.mes, true)}
    </text>
  ));

  return (
    <section aria-label="Gráficos dos últimos 6 meses" className="flex flex-col gap-3.5">
      <div className="grid grid-cols-[repeat(auto-fit,minmax(280px,1fr))] gap-3.5">
        <figure className="cartao m-0 flex flex-col gap-3">
          <figcaption className="flex flex-wrap items-center justify-between gap-2">
            <span className="font-extrabold">Custos fixos x variáveis</span>
            <span className="flex gap-3 text-[13px] font-semibold text-muted">
              <span className="flex items-center gap-1.5"><span className="size-3 rounded-sm" style={{ background: COR_FIXO }} />Fixos</span>
              <span className="flex items-center gap-1.5"><span className="size-3 rounded-sm" style={{ background: COR_VARIAVEL }} />Variáveis</span>
            </span>
          </figcaption>
          <svg viewBox={`0 0 ${L} ${A}`} className="w-full" role="img" aria-label="Custos fixos e variáveis por mês">
            <line x1={0} x2={L} y1={BASE_Y} y2={BASE_Y} stroke="#d5dfdc" />
            {meses.map((m, i) => {
              const larg = Math.min(18, passo / 3);
              const x = i * passo + passo / 2 - larg - 1;
              return (
                <g key={m.mes}>
                  <title>{`${nomeMes(m.mes)}\nFixos: ${reais(m.totalFixo)}\nVariáveis: ${reais(m.totalVariavel)}`}</title>
                  <rect x={i * passo} y={0} width={passo} height={BASE_Y} fill="transparent" />
                  <path d={barra(x, larg, BASE_Y, yCusto(m.totalFixo))} fill={COR_FIXO} />
                  <path d={barra(x + larg + 2, larg, BASE_Y, yCusto(m.totalVariavel))} fill={COR_VARIAVEL} />
                </g>
              );
            })}
            {rotulos}
          </svg>
        </figure>

        <figure className="cartao m-0 flex flex-col gap-3">
          <figcaption className="font-extrabold">Lucro por mês</figcaption>
          <svg viewBox={`0 0 ${L} ${A}`} className="w-full" role="img" aria-label="Lucro por mês">
            {meses.map((m, i) => {
              const larg = Math.min(28, passo / 2);
              const x = i * passo + passo / 2 - larg / 2;
              const atual = i === meses.length - 1;
              return (
                <g key={m.mes}>
                  <title>{`${nomeMes(m.mes)}: ${reais(m.lucro)}`}</title>
                  <rect x={i * passo} y={0} width={passo} height={BASE_Y} fill="transparent" />
                  <path d={barra(x, larg, zero, yLucro(m.lucro))} fill={m.lucro >= 0 ? COR_FIXO : COR_PREJUIZO} />
                  {atual && (
                    <text x={x + larg} y={m.lucro >= 0 ? yLucro(m.lucro) - 4 : zero - 4} textAnchor="end" className="fill-ink text-[11px] font-bold">
                      {compacto(m.lucro)}
                    </text>
                  )}
                </g>
              );
            })}
            <line x1={0} x2={L} y1={zero} y2={zero} stroke="#9fb0ac" />
            {rotulos}
          </svg>
        </figure>
      </div>

      <details className="cartao py-3">
        <summary className="cursor-pointer font-semibold">Ver em tabela</summary>
        <div className="overflow-x-auto">
          <table className="mt-3 w-full border-collapse text-sm">
            <thead>
              <tr className="text-left text-muted">
                <th className="py-1.5 pr-3">Mês</th>
                <th className="py-1.5 pr-3 text-right">Receita</th>
                <th className="py-1.5 pr-3 text-right">Fixos</th>
                <th className="py-1.5 pr-3 text-right">Variáveis</th>
                <th className="py-1.5 pr-3 text-right">Eventuais</th>
                <th className="py-1.5 text-right">Lucro</th>
              </tr>
            </thead>
            <tbody>
              {meses.map((m) => (
                <tr key={m.mes} className="border-t border-linha">
                  <td className="py-1.5 pr-3">{nomeMes(m.mes)}</td>
                  <td className="py-1.5 pr-3 text-right">{reais(m.receita)}</td>
                  <td className="py-1.5 pr-3 text-right">{reais(m.totalFixo)}</td>
                  <td className="py-1.5 pr-3 text-right">{reais(m.totalVariavel)}</td>
                  <td className="py-1.5 pr-3 text-right">{reais(m.totalEventual)}</td>
                  <td className="py-1.5 text-right font-bold">{reais(m.lucro)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </details>
    </section>
  );
}
