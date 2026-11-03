"use client";

import { useCallback, useEffect, useState } from "react";
import { Carregando, Erro, Titulo } from "@/components/Ui";
import { alterarUsuario, criarUsuario, listarUsuarios, redefinirSenha } from "@/lib/api";
import type { Papel, UsuarioPainel } from "@/lib/tipos";

/** Dono: cadastra funcionários, desativa quem saiu e redefine senha de quem esqueceu. */
export default function Usuarios() {
  const [lista, setLista] = useState<UsuarioPainel[] | null>(null);
  const [erro, setErro] = useState<string | null>(null);
  const [aviso, setAviso] = useState<string | null>(null);
  const [novo, setNovo] = useState({ nome: "", login: "", senha: "", papel: "FUNCIONARIO" as Papel });

  const carregar = useCallback(() => listarUsuarios().then(setLista).catch((e: Error) => setErro(e.message)), []);
  useEffect(() => {
    carregar();
  }, [carregar]);

  async function acao(fazer: () => Promise<string>) {
    setErro(null);
    setAviso(null);
    try {
      setAviso(await fazer());
      await carregar();
    } catch (e) {
      setErro((e as Error).message);
    }
  }

  return (
    <>
      <Titulo sobre="Painel da loja" titulo="Usuários" />
      <p className="m-0 max-w-[760px] text-muted">
        Cada pessoa com seu login: o histórico mostra quem fez cada alteração. <b>Funcionário</b> usa agenda, clientes, pets,
        vacinas e lembretes; não vê financeiro, campanhas, configurações nem exclui clientes. A <b>conta principal</b> do servidor
        continua funcionando como dono e serve para recuperar o acesso se alguém esquecer a senha.
      </p>
      <Erro texto={erro} />
      {aviso && <p role="status" className="m-0 rounded-xl bg-[#ddf2ea] px-4 py-3 font-semibold text-[#0b5e4f]">{aviso}</p>}

      <form
        className="cartao grid grid-cols-[repeat(auto-fit,minmax(180px,1fr))] items-end gap-3.5"
        onSubmit={(e) => {
          e.preventDefault();
          acao(async () => {
            const u = await criarUsuario(novo.nome, novo.login, novo.senha, novo.papel);
            setNovo({ nome: "", login: "", senha: "", papel: "FUNCIONARIO" });
            return `Usuário ${u.login} criado. Passe o login e a senha para a pessoa, e peça para ela trocar em "Minha senha".`;
          });
        }}
      >
        <h2 className="col-span-full m-0 text-lg font-extrabold">Novo usuário</h2>
        <label className="flex flex-col gap-1.5"><span className="rotulo">Nome</span>
          <input className="campo" required maxLength={80} value={novo.nome} onChange={(e) => setNovo({ ...novo, nome: e.target.value })} /></label>
        <label className="flex flex-col gap-1.5"><span className="rotulo">Login</span>
          <input className="campo" required pattern="[a-zA-Z0-9._-]{3,40}" autoComplete="off" value={novo.login} onChange={(e) => setNovo({ ...novo, login: e.target.value })} /></label>
        <label className="flex flex-col gap-1.5"><span className="rotulo">Senha inicial (mín. 10)</span>
          <input className="campo" required type="password" minLength={10} maxLength={72} autoComplete="new-password" value={novo.senha} onChange={(e) => setNovo({ ...novo, senha: e.target.value })} /></label>
        <label className="flex flex-col gap-1.5"><span className="rotulo">Papel</span>
          <select className="campo" value={novo.papel} onChange={(e) => setNovo({ ...novo, papel: e.target.value as Papel })}>
            <option value="FUNCIONARIO">Funcionário</option>
            <option value="DONO">Dono</option>
          </select></label>
        <button type="submit" className="btn btn-verde">Criar usuário</button>
      </form>

      <section className="cartao flex flex-col gap-2">
        <h2 className="m-0 text-lg font-extrabold">Pessoas com acesso</h2>
        {!lista ? <Carregando /> : lista.length === 0 ? (
          <p className="m-0 text-muted">Só a conta principal por enquanto.</p>
        ) : (
          <ul className="m-0 flex list-none flex-col p-0">
            {lista.map((u) => (
              <li key={u.id} className="flex flex-wrap items-center gap-x-4 gap-y-2 border-b border-linha py-3 last:border-0">
                <span className="flex min-w-0 flex-[1_1_220px] flex-col">
                  <span className="font-bold">{u.nome} {!u.ativo && <span className="chip ml-1 bg-[#e9eceb] text-[#3f4b48]">Desativado</span>}</span>
                  <span className="text-sm text-muted">login: {u.login} · {u.papel === "DONO" ? "dono" : "funcionário"}</span>
                </span>
                <button type="button" className="btn btn-claro min-h-10"
                  onClick={() => acao(async () => {
                    await alterarUsuario(u.id, u.nome, u.papel, !u.ativo);
                    return u.ativo ? `${u.login} desativado: não entra mais.` : `${u.login} reativado.`;
                  })}>
                  {u.ativo ? "Desativar" : "Reativar"}
                </button>
                <button type="button" className="btn btn-claro min-h-10"
                  onClick={() => acao(async () => {
                    await alterarUsuario(u.id, u.nome, u.papel === "DONO" ? "FUNCIONARIO" : "DONO", u.ativo);
                    return `${u.login} agora é ${u.papel === "DONO" ? "funcionário" : "dono"}.`;
                  })}>
                  {u.papel === "DONO" ? "Tornar funcionário" : "Tornar dono"}
                </button>
                <RedefinirSenha aoSalvar={(senha) => acao(async () => {
                  await redefinirSenha(u.id, senha);
                  return `Senha de ${u.login} redefinida. Peça para a pessoa trocar em "Minha senha".`;
                })} />
              </li>
            ))}
          </ul>
        )}
      </section>
    </>
  );
}

function RedefinirSenha({ aoSalvar }: { aoSalvar: (senha: string) => void }) {
  const [aberto, setAberto] = useState(false);
  const [senha, setSenha] = useState("");
  if (!aberto) return <button type="button" className="btn btn-claro min-h-10" onClick={() => setAberto(true)}>Redefinir senha</button>;
  return (
    <form className="flex flex-wrap gap-2" onSubmit={(e) => { e.preventDefault(); aoSalvar(senha); setSenha(""); setAberto(false); }}>
      <input className="campo min-h-10 w-56" type="password" required minLength={10} maxLength={72} autoComplete="new-password"
        placeholder="Nova senha (mín. 10)" aria-label="Nova senha" value={senha} onChange={(e) => setSenha(e.target.value)} />
      <button type="submit" className="btn btn-verde min-h-10">Salvar</button>
      <button type="button" className="btn btn-claro min-h-10" onClick={() => setAberto(false)}>Cancelar</button>
    </form>
  );
}
