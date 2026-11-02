"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { Marca } from "@/components/Pata";
import { entrar, modoDemonstracao } from "@/lib/api";

export default function Login() {
  const router = useRouter();
  const [usuario, setUsuario] = useState("");
  const [senha, setSenha] = useState("");
  const [erro, setErro] = useState<string | null>(null);
  const [enviando, setEnviando] = useState(false);

  async function enviar(e: React.FormEvent) {
    e.preventDefault();
    setErro(null);
    setEnviando(true);
    try {
      await entrar(usuario, senha);
      router.push("/painel");
    } catch (er) {
      setErro((er as Error).message);
    } finally {
      setEnviando(false);
    }
  }

  return (
    <main className="flex min-h-screen items-center justify-center bg-verde-escuro p-6">
      <form onSubmit={enviar} className="cartao flex w-full max-w-[380px] flex-col gap-4 p-8">
        <Marca subtitulo="Área da loja" />
        <h1 className="m-0 mt-2 text-2xl font-extrabold">Entrar</h1>
        {modoDemonstracao && <p className="m-0 rounded-xl bg-[#fdecc8] p-3 text-sm text-[#7a4a00]">Modo demonstração: clique em Entrar.</p>}
        <label className="flex flex-col gap-1.5">
          <span className="rotulo">Usuário</span>
          <input className="campo" autoComplete="username" value={usuario} onChange={(e) => setUsuario(e.target.value)} />
        </label>
        <label className="flex flex-col gap-1.5">
          <span className="rotulo">Senha</span>
          <input className="campo" type="password" autoComplete="current-password" value={senha} onChange={(e) => setSenha(e.target.value)} />
        </label>
        {erro && <p role="alert" className="m-0 text-sm text-[#9b2c12]">{erro}</p>}
        <button type="submit" disabled={enviando} className="btn btn-verde">{enviando ? "Entrando…" : "Entrar"}</button>
      </form>
    </main>
  );
}
