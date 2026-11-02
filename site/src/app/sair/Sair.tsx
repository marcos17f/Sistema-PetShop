"use client";

import Link from "next/link";
import { useState } from "react";
import { descadastrar } from "@/lib/api";

/** Link de toda campanha: o cliente sai da lista de promoções com um clique (LGPD). */
export function Sair() {
  const [estado, setEstado] = useState<"pergunta" | "saindo" | "pronto">("pergunta");

  async function sair() {
    setEstado("saindo");
    const token = new URLSearchParams(window.location.search).get("t") ?? "";
    try {
      await descadastrar(token);
    } finally {
      setEstado("pronto"); // mesma resposta sempre: não revela se o link existe
    }
  }

  return (
    <main className="flex min-h-screen items-center justify-center bg-fundo p-5">
      <section className="cartao flex w-full max-w-[460px] flex-col gap-4 p-8 text-center">
        {estado === "pronto" ? (
          <>
            <h1 className="m-0 text-2xl font-extrabold">Pronto!</h1>
            <p className="m-0">Você não vai mais receber promoções. Lembretes de banho, vacina e agendamentos continuam normalmente.</p>
          </>
        ) : (
          <>
            <h1 className="m-0 text-2xl font-extrabold">Sair da lista de promoções?</h1>
            <p className="m-0 text-muted">Você para de receber promoções pelo WhatsApp. Dá para voltar quando quiser, é só pedir na loja.</p>
            <button type="button" className="btn btn-verde" disabled={estado === "saindo"} onClick={sair}>
              {estado === "saindo" ? "Saindo…" : "Sim, não quero mais receber"}
            </button>
          </>
        )}
        <Link href="/" className="text-sm font-semibold text-verde">Ir para o site</Link>
      </section>
    </main>
  );
}
