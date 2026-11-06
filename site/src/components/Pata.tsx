import Image from "next/image";
import { loja } from "@/lib/loja";

export function Pata({ tamanho = 22, cor = "#25488a" }: { tamanho?: number; cor?: string }) {
  return (
    <svg width={tamanho} height={tamanho} viewBox="0 0 24 24" fill="none" stroke={cor} strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <circle cx="6" cy="9" r="2" />
      <circle cx="10" cy="5" r="2" />
      <circle cx="14" cy="5" r="2" />
      <circle cx="18" cy="9" r="2" />
      <path d="M8 17c0-3 2-5 4-5s4 2 4 5c0 2-2 3-4 3s-4-1-4-3z" />
    </svg>
  );
}

export function Marca({ subtitulo, nome = loja.nome }: { subtitulo?: string; nome?: string }) {
  return (
    <span className="flex items-center gap-3">
      <Image src="/logo-arca.jpg" alt={`Logo ${nome}`} width={44} height={44} className="size-11 rounded-xl ring-2 ring-white/70" />
      <span className="flex flex-col leading-tight">
        <span className="font-titulo text-lg font-semibold">{nome}</span>
        {subtitulo && <span className="text-xs opacity-75">{subtitulo}</span>}
      </span>
    </span>
  );
}
