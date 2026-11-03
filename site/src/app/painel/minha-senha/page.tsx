"use client";

import { useEffect, useState } from "react";
import { Erro, Titulo } from "@/components/Ui";
import { quemSouEu, trocarMinhaSenha } from "@/lib/api";
import type { Eu } from "@/lib/tipos";

export default function MinhaSenha() {
  const [eu, setEu] = useState<Eu | null>(null);
  const [atual, setAtual] = useState("");
  const [nova, setNova] = useState("");
  const [repetir, setRepetir] = useState("");
  const [erro, setErro] = useState<string | null>(null);
  const [ok, setOk] = useState(false);

  useEffect(() => {
    quemSouEu().then(setEu).catch((e: Error) => setErro(e.message));
  }, []);

  async function salvar(e: React.FormEvent) {
    e.preventDefault();
    setErro(null);
    setOk(false);
    if (nova !== repetir) return setErro("A nova senha e a repetição não são iguais.");
    try {
      await trocarMinhaSenha(atual, nova);
      setOk(true);
      setAtual("");
      setNova("");
      setRepetir("");
    } catch (er) {
      setErro((er as Error).message);
    }
  }

  return (
    <>
      <Titulo sobre="Painel da loja" titulo="Minha senha" />
      {eu?.contaPrincipal ? (
        <p className="cartao m-0 max-w-[620px]">
          Você entrou com a <b>conta principal</b> do servidor. A senha dela é trocada no servidor (variável PAINEL_SENHA, README).
          Para o dia a dia, crie um usuário seu em <b>Usuários</b>.
        </p>
      ) : (
        <form onSubmit={salvar} className="cartao flex max-w-[460px] flex-col gap-3.5">
          <label className="flex flex-col gap-1.5"><span className="rotulo">Senha atual</span>
            <input className="campo" type="password" required autoComplete="current-password" value={atual} onChange={(e) => setAtual(e.target.value)} /></label>
          <label className="flex flex-col gap-1.5"><span className="rotulo">Nova senha (mín. 10, sem o seu login)</span>
            <input className="campo" type="password" required minLength={10} maxLength={72} autoComplete="new-password" value={nova} onChange={(e) => setNova(e.target.value)} /></label>
          <label className="flex flex-col gap-1.5"><span className="rotulo">Repita a nova senha</span>
            <input className="campo" type="password" required autoComplete="new-password" value={repetir} onChange={(e) => setRepetir(e.target.value)} /></label>
          <Erro texto={erro} />
          {ok && <p role="status" className="m-0 rounded-xl bg-[#ddf2ea] px-4 py-3 font-semibold text-[#0b5e4f]">Senha trocada.</p>}
          <button type="submit" className="btn btn-verde">Trocar senha</button>
        </form>
      )}
    </>
  );
}
