import Image from "next/image";
import Link from "next/link";
import { Marca, Pata } from "@/components/Pata";
import { linkWhatsapp, loja } from "@/lib/loja";
import { carregarLoja } from "@/lib/lojaPublica";

const ICONES: Record<string, string> = {
  Banho: "🛁", Tosa: "✂️", "Banho & tosa": "🐶", Hidratação: "💧", "Unhas e ouvidos": "🐾", Acessórios: "🎀", Vacinas: "💉", Consultas: "🩺",
};
const FUNDOS = ["bg-[#e8eefa]", "bg-[#fdf0dc]", "bg-[#e9f5ee]", "bg-[#f6e9df]"];

/** Site público da loja (simples: o foco é levar ao WhatsApp e aparecer no Google). Dados vêm da tela Configurações. */
export default async function Site() {
  const c = await carregarLoja();
  const whats = c.whatsapp
    ? linkWhatsapp(c.whatsapp, `Olá! Vim pelo site da ${c.nome} e gostaria de agendar um horário.`)
    : "#contato";
  const site = process.env.NEXT_PUBLIC_SITE_URL ?? "http://localhost:3001";
  const instagram = (u: string) => `https://www.instagram.com/${u}/`;
  const enderecoCompleto = [c.endereco, c.cidade].filter(Boolean).join(", ");
  // SEO local: o Google entende que é um pet shop, onde fica, telefone e horário (dados da tela Configurações)
  const dias = ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday"];
  const jsonLd = {
    "@context": "https://schema.org",
    "@type": "PetStore",
    name: c.nome,
    alternateName: loja.nomeAlternativo,
    slogan: loja.slogan,
    url: site,
    logo: `${site}/logo-arca.jpg`,
    image: `${site}/logo-arca.jpg`,
    telephone: c.whatsapp ? `+55${c.whatsapp.replace(/\D/g, "").replace(/^55/, "")}` : undefined,
    address: {
      "@type": "PostalAddress",
      streetAddress: c.endereco || undefined,
      addressLocality: c.cidade ? c.cidade.split(/\s+[–-]\s+/)[0] : undefined,
      addressRegion: "GO",
      addressCountry: "BR",
    },
    areaServed: loja.areaAtendida.map((name) => ({ "@type": "City", name: `${name} – GO` })),
    openingHoursSpecification: [
      { "@type": "OpeningHoursSpecification", dayOfWeek: dias, opens: c.abre, closes: c.fecha },
      ...(c.sabadoAbre && c.sabadoFecha ? [{ "@type": "OpeningHoursSpecification", dayOfWeek: ["Saturday"], opens: c.sabadoAbre, closes: c.sabadoFecha }] : []),
    ],
    sameAs: [c.instagram && instagram(c.instagram), loja.responsavel.facebook].filter(Boolean),
    founder: { "@type": "Person", name: loja.responsavel.nome, alternateName: loja.responsavel.apelido, sameAs: [instagram(loja.responsavel.instagram)] },
    potentialAction: { "@type": "ReserveAction", target: `${site}/agendar` },
  };
  return (
    <>
      {/* dados da própria loja; "<" escapado para nunca fechar a tag script */}
      <script type="application/ld+json" dangerouslySetInnerHTML={{ __html: JSON.stringify(jsonLd).replace(/</g, "\\u003c") }} />

      <div className="bg-verde-escuro text-center text-sm font-semibold text-white">
        <p className="m-0 px-6 py-2">🐾 Mais de 20 anos de experiência cuidando do seu pet · {c.cidade}</p>
      </div>

      <header className="sticky top-0 z-10 border-b border-linha bg-white text-ink">
        <div className="mx-auto flex max-w-[1100px] flex-wrap items-center justify-between gap-4 px-6 py-3">
          <Marca nome={c.nome} subtitulo="Banho, tosa e cuidados pet" />
          <nav className="flex flex-wrap items-center gap-5 text-sm font-semibold">
            <a href="#servicos" className="text-ink no-underline hover:text-verde">Serviços</a>
            <a href="#sobre" className="text-ink no-underline hover:text-verde">Sobre</a>
            <a href="#contato" className="text-ink no-underline hover:text-verde">Contato</a>
            <Link href="/painel" className="text-muted no-underline hover:text-verde">Área da loja</Link>
            <Link href="/agendar" className="btn btn-verde min-h-10 rounded-full">Agendar</Link>
          </nav>
        </div>
      </header>

      <main>
        <nav aria-label="Atalhos de serviços" className="mx-auto max-w-[1100px] px-6 pt-6">
          <ul className="m-0 grid list-none grid-cols-[repeat(auto-fit,minmax(150px,1fr))] gap-3 p-0">
            {loja.servicos.slice(0, 5).map((s, i) => (
              <li key={s.nome}>
                <a href="#servicos" className={`flex items-center gap-3 rounded-2xl px-4 py-3 font-semibold text-ink no-underline transition hover:-translate-y-0.5 ${FUNDOS[i % FUNDOS.length]}`}>
                  <span aria-hidden="true" className="text-2xl">{ICONES[s.nome] ?? "🐾"}</span>
                  {s.nome}
                </a>
              </li>
            ))}
          </ul>
        </nav>

        <section className="mx-auto max-w-[1100px] px-6 pt-6">
          <div className="relative overflow-hidden rounded-[32px] bg-verde-escuro text-white">
            <div className="grid items-center gap-8 px-8 py-12 md:grid-cols-[1.3fr_1fr] md:px-14">
              <div className="flex flex-col gap-5">
                <span className="chip w-fit bg-caramelo text-ink">⭐ Mais de 20 anos de experiência</span>
                <h1 className="font-titulo m-0 text-[clamp(34px,5vw,56px)] font-semibold leading-[1.05]">{loja.slogan}</h1>
                <p className="m-0 max-w-[520px] text-lg text-menta">
                  Banho, tosa e cuidados feitos com carinho, do jeitinho que o seu pet merece. Agende pelo WhatsApp: a gente avisa quando chegar o dia do próximo banho.
                </p>
                <div className="flex flex-wrap gap-3">
                  <Link href="/agendar" className="btn rounded-full bg-caramelo text-ink">Agendar online</Link>
                  <a href={whats} className="btn rounded-full border-white/60 text-white">Agendar pelo WhatsApp</a>
                </div>
              </div>
              <div className="flex justify-center">
                <Image src="/logo-arca.jpg" alt={`Logo ${c.nome}: arca com cachorro e gato`} width={300} height={300} priority className="w-full max-w-[300px] rounded-[28px]" />
              </div>
            </div>
            {/* ondas, como no logo */}
            <svg aria-hidden="true" viewBox="0 0 1200 60" preserveAspectRatio="none" className="block h-8 w-full text-[#1c3a73]">
              <path fill="currentColor" d="M0 30c100-25 200-25 300 0s200 25 300 0 200-25 300 0 200 25 300 0v30H0z" />
            </svg>
          </div>
        </section>

        <section id="servicos" className="mx-auto flex max-w-[1100px] flex-col gap-6 px-6 py-16">
          <div className="flex flex-col gap-1">
            <h2 className="font-titulo m-0 text-3xl font-semibold">Nossos serviços</h2>
            <p className="m-0 text-muted">Tudo para o seu pet sair limpinho, cheiroso e feliz.</p>
          </div>
          <div className="grid grid-cols-[repeat(auto-fit,minmax(220px,1fr))] gap-4">
            {loja.servicos.map((s, i) => (
              <div key={s.nome} className="cartao flex flex-col gap-3 border border-linha transition hover:-translate-y-1 hover:shadow-lg">
                <span aria-hidden="true" className={`inline-flex size-14 items-center justify-center rounded-2xl text-3xl ${FUNDOS[i % FUNDOS.length]}`}>{ICONES[s.nome] ?? "🐾"}</span>
                <h3 className="font-titulo m-0 text-xl font-semibold">{s.nome}</h3>
                <p className="m-0 flex-1 text-muted">{s.texto}</p>
                <Link href="/agendar" aria-label={`Agendar ${s.nome}`} className="ml-auto inline-flex size-10 items-center justify-center rounded-full bg-verde text-xl font-bold text-white no-underline hover:bg-[#1c3a73]">+</Link>
              </div>
            ))}
          </div>
        </section>

        <section id="sobre" className="bg-white">
          <div className="mx-auto grid max-w-[1100px] items-center gap-8 px-6 py-16 md:grid-cols-[auto_1fr]">
            <span className="inline-flex size-28 items-center justify-center rounded-full bg-[#fdf0dc]"><Pata tamanho={56} /></span>
            <div className="flex flex-col gap-3">
              <h2 className="font-titulo m-0 text-3xl font-semibold">Mais de 20 anos cuidando de quem você ama</h2>
              <p className="m-0 text-lg text-muted">
                {loja.nomeAlternativo} nasceu da experiência de {loja.responsavel.nome}, o {loja.responsavel.apelido}, que há mais de 20 anos
                dá banho, tosa e carinho em cães e gatos. Aqui cada pet é tratado com calma e paciência, como se fosse da família.
              </p>
              <a href={instagram(loja.responsavel.instagram)} target="_blank" rel="noopener noreferrer" className="w-fit font-semibold text-verde">
                Conheça o trabalho do Cleiton: @{loja.responsavel.instagram}
              </a>
            </div>
          </div>
        </section>

        <section id="contato" className="mx-auto grid max-w-[1100px] grid-cols-[repeat(auto-fit,minmax(280px,1fr))] gap-6 px-6 py-16">
          <div className="flex flex-col gap-3">
            <h2 className="font-titulo m-0 text-3xl font-semibold">Onde estamos</h2>
            <p className="m-0 text-muted">📍 {enderecoCompleto}</p>
            <p className="m-0 text-muted">🕒 {c.horarioTexto}</p>
            {c.instagram && (
              <a href={instagram(c.instagram)} target="_blank" rel="noopener noreferrer" className="font-semibold text-verde">
                @{c.instagram} no Instagram
              </a>
            )}
            <div className="mt-2 flex flex-wrap gap-3">
              <a href={whats} className="btn btn-verde rounded-full">Chamar no WhatsApp</a>
              {c.linkAvaliacao && (
                <a href={c.linkAvaliacao} target="_blank" rel="noopener noreferrer" className="btn btn-claro rounded-full">Avalie no Google</a>
              )}
            </div>
            <p className="m-0 text-sm text-muted">Depois do atendimento, sua avaliação no Google ajuda muito a loja.</p>
          </div>
          {enderecoCompleto && (
            <iframe
              title={`Mapa: ${c.nome}`}
              src={`https://www.google.com/maps?q=${encodeURIComponent(enderecoCompleto)}&output=embed`}
              loading="lazy"
              referrerPolicy="no-referrer-when-downgrade"
              className="min-h-[280px] w-full rounded-[22px] border-0"
            />
          )}
        </section>
      </main>

      <footer className="bg-verde-escuro text-menta">
        <div className="mx-auto flex max-w-[1100px] flex-wrap items-center justify-between gap-4 px-6 py-8 text-sm">
          <span className="font-titulo text-lg font-semibold text-white">{loja.nomeAlternativo}</span>
          <span>{enderecoCompleto}</span>
          <span>© {new Date().getFullYear()} {c.nome} · Site por MarcosLab</span>
        </div>
      </footer>
    </>
  );
}
