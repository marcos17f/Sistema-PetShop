import Link from "next/link";
import { Marca } from "@/components/Pata";
import { linkWhatsapp, loja } from "@/lib/loja";
import { carregarLoja } from "@/lib/lojaPublica";

/** Site público da loja (simples: o foco é levar ao WhatsApp e aparecer no Google). Dados vêm da tela Configurações. */
export default async function Site() {
  const c = await carregarLoja();
  const whats = c.whatsapp ? linkWhatsapp(c.whatsapp, "Olá! Quero agendar um horário.") : "#contato";
  const site = process.env.NEXT_PUBLIC_SITE_URL ?? "http://localhost:3001";
  // SEO local: o Google entende que é um pet shop, onde fica, telefone e horário (dados da tela Configurações)
  const dias = ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday"];
  const jsonLd = {
    "@context": "https://schema.org",
    "@type": "PetStore",
    name: c.nome,
    url: site,
    telephone: c.whatsapp ? `+55${c.whatsapp.replace(/\D/g, "").replace(/^55/, "")}` : undefined,
    address: { "@type": "PostalAddress", streetAddress: c.endereco || undefined, addressLocality: c.cidade || undefined, addressCountry: "BR" },
    openingHoursSpecification: [
      { "@type": "OpeningHoursSpecification", dayOfWeek: dias, opens: c.abre, closes: c.fecha },
      ...(c.sabadoAbre && c.sabadoFecha ? [{ "@type": "OpeningHoursSpecification", dayOfWeek: ["Saturday"], opens: c.sabadoAbre, closes: c.sabadoFecha }] : []),
    ],
    sameAs: c.instagram ? [`https://instagram.com/${c.instagram}`] : undefined,
    potentialAction: { "@type": "ReserveAction", target: `${site}/agendar` },
  };
  return (
    <>
      {/* dados da própria loja; "<" escapado para nunca fechar a tag script */}
      <script type="application/ld+json" dangerouslySetInnerHTML={{ __html: JSON.stringify(jsonLd).replace(/</g, "\\u003c") }} />
      <header className="bg-verde-escuro text-menta">
        <div className="mx-auto flex max-w-[1100px] flex-wrap items-center justify-between gap-4 px-6 py-5">
          <Marca nome={c.nome} subtitulo="Banho, tosa e vacinas" />
          <nav className="flex flex-wrap items-center gap-5 text-sm font-semibold">
            <a href="#servicos" className="text-menta no-underline">Serviços</a>
            <a href="#contato" className="text-menta no-underline">Contato</a>
            <Link href="/painel" className="text-menta-2 no-underline">Área da loja</Link>
          </nav>
        </div>
      </header>

      <main>
        <section className="bg-verde-escuro pb-20 text-white">
          <div className="mx-auto flex max-w-[1100px] flex-col gap-6 px-6 pt-10">
            <h1 className="m-0 max-w-[720px] text-[clamp(36px,5vw,60px)] font-extrabold leading-[1.05] tracking-tight">
              Cuidado com o seu pet, do banho à vacina.
            </h1>
            <p className="m-0 max-w-[560px] text-lg text-menta">
              Agende pelo WhatsApp. A gente avisa quando chegar o dia do próximo banho ou da próxima vacina.
            </p>
            <div className="flex flex-wrap gap-3">
              <Link href="/agendar" className="btn bg-caramelo text-verde-escuro">Agendar online</Link>
              <a href={whats} className="btn border-verde-linha text-white">Agendar pelo WhatsApp</a>
              <a href="#servicos" className="btn border-verde-linha text-white">Ver serviços</a>
            </div>
          </div>
        </section>

        <section id="servicos" className="mx-auto flex max-w-[1100px] flex-col gap-6 px-6 py-20">
          <h2 className="m-0 text-3xl font-extrabold">Serviços</h2>
          <div className="grid grid-cols-[repeat(auto-fit,minmax(220px,1fr))] gap-4">
            {loja.servicos.map((s) => (
              <div key={s.nome} className="cartao flex flex-col gap-2">
                <h3 className="m-0 text-xl font-extrabold">{s.nome}</h3>
                <p className="m-0 text-muted">{s.texto}</p>
              </div>
            ))}
          </div>
        </section>

        <section id="contato" className="bg-white">
          <div className="mx-auto grid max-w-[1100px] grid-cols-[repeat(auto-fit,minmax(260px,1fr))] gap-6 px-6 py-16">
            <div className="flex flex-col gap-2">
              <h2 className="m-0 text-3xl font-extrabold">Onde estamos</h2>
              <p className="m-0 text-muted">{[c.endereco, c.cidade].filter(Boolean).join(", ")}</p>
              <p className="m-0 text-muted">{c.horarioTexto}</p>
              {c.instagram && (
                <a href={`https://instagram.com/${c.instagram}`} target="_blank" rel="noopener noreferrer" className="font-semibold text-verde">
                  @{c.instagram} no Instagram
                </a>
              )}
            </div>
            <div className="flex flex-col items-start gap-3">
              <a href={whats} className="btn btn-verde">Chamar no WhatsApp</a>
              {c.linkAvaliacao && (
                <a href={c.linkAvaliacao} target="_blank" rel="noopener noreferrer" className="btn btn-claro">Avalie no Google</a>
              )}
              <p className="m-0 text-sm text-muted">Depois do atendimento, sua avaliação no Google ajuda muito a loja.</p>
            </div>
          </div>
        </section>
      </main>
      <footer className="py-8 text-center text-sm text-muted">© {new Date().getFullYear()} {c.nome} · Site por MarcosLab</footer>
    </>
  );
}
