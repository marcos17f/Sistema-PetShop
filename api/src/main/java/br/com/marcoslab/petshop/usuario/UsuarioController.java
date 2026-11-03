package br.com.marcoslab.petshop.usuario;

import br.com.marcoslab.petshop.auditoria.Auditoria.Acao;
import br.com.marcoslab.petshop.auditoria.AuditoriaService;
import br.com.marcoslab.petshop.erro.Erros;
import br.com.marcoslab.petshop.loja.Loja;
import br.com.marcoslab.petshop.loja.LojaService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Clock;
import java.util.List;
import java.util.Locale;

/** Logins por pessoa: o dono cadastra, ativa/desativa e redefine senhas; cada um troca a própria senha. */
@RestController
@RequestMapping("/api/lojas/{slug}/usuarios")
public class UsuarioController {

    static final String SENHA = "A senha precisa ter de 10 a 72 caracteres";

    public record Eu(String login, String nome, Papel papel, boolean contaPrincipal) {
    }

    public record UsuarioDto(Long id, String nome, String login, Papel papel, boolean ativo) {
        static UsuarioDto de(Usuario u) {
            return new UsuarioDto(u.getId(), u.getNome(), u.getLogin(), u.getPapel(), u.isAtivo());
        }
    }

    public record NovoUsuario(
            @NotBlank(message = "Informe o nome") @Size(max = 80) String nome,
            @NotBlank(message = "Informe o login")
            @Pattern(regexp = "[a-zA-Z0-9._-]{3,40}", message = "Login: 3 a 40 letras, números, ponto, traço ou _") String login,
            @NotNull(message = SENHA) @Size(min = 10, max = 72, message = SENHA) String senha,
            @NotNull(message = "Escolha o papel") Papel papel) {
    }

    public record AlterarUsuario(@NotBlank(message = "Informe o nome") @Size(max = 80) String nome,
                                 @NotNull Papel papel, boolean ativo) {
    }

    public record NovaSenha(@NotNull(message = SENHA) @Size(min = 10, max = 72, message = SENHA) String senha) {
    }

    public record TrocaDeSenha(@NotBlank(message = "Informe a senha atual") String atual,
                               @NotNull(message = SENHA) @Size(min = 10, max = 72, message = SENHA) String nova) {
    }

    private final LojaService lojas;
    private final UsuarioRepository usuarios;
    private final ContasDoPainel contas;
    private final PasswordEncoder senhas;
    private final AuditoriaService auditoria;
    private final Clock clock;

    public UsuarioController(LojaService lojas, UsuarioRepository usuarios, ContasDoPainel contas, PasswordEncoder senhas,
                             AuditoriaService auditoria, Clock clock) {
        this.lojas = lojas;
        this.usuarios = usuarios;
        this.contas = contas;
        this.senhas = senhas;
        this.auditoria = auditoria;
        this.clock = clock;
    }

    /** Quem está logado e o papel (o painel esconde do funcionário o que ele não pode usar). */
    @GetMapping("/eu")
    @Transactional(readOnly = true)
    public Eu eu(@PathVariable String slug, Authentication auth) {
        lojas.buscar(slug);
        if (contas.ehContaPrincipal(auth.getName())) return new Eu(auth.getName(), "Conta principal", Papel.DONO, true);
        Usuario u = usuarios.findByLogin(auth.getName()).orElseThrow();
        return new Eu(u.getLogin(), u.getNome(), u.getPapel(), false);
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<UsuarioDto> listar(@PathVariable String slug) {
        return usuarios.findByLojaIdOrderByNome(lojas.buscar(slug).getId()).stream().map(UsuarioDto::de).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public UsuarioDto criar(@PathVariable String slug, @Valid @RequestBody NovoUsuario dados) {
        Loja loja = lojas.buscar(slug);
        String login = dados.login().trim().toLowerCase(Locale.ROOT);
        if (contas.ehContaPrincipal(login) || usuarios.existsByLogin(login)) {
            throw new Erros.Conflito("Esse login já existe. Escolha outro.");
        }
        senhaDiferenteDoLogin(dados.senha(), login);
        Usuario u = usuarios.save(new Usuario(loja, dados.nome().trim(), login, senhas.encode(dados.senha()), dados.papel(),
                clock.instant()));
        auditoria.registrar(loja, Acao.CRIOU, "usuario", u.getId(), null, u.getLogin() + " · " + u.getPapel());
        return UsuarioDto.de(u);
    }

    @PutMapping("/{id}")
    @Transactional
    public UsuarioDto alterar(@PathVariable String slug, @PathVariable Long id, @Valid @RequestBody AlterarUsuario dados,
                              Authentication auth) {
        Loja loja = lojas.buscar(slug);
        Usuario u = doLoja(loja, id);
        if (u.getLogin().equals(auth.getName()) && (!dados.ativo() || dados.papel() != Papel.DONO)) {
            throw new Erros.Conflito("Você não pode desativar nem rebaixar a sua própria conta.");
        }
        String antes = u.getLogin() + " · " + u.getPapel() + (u.isAtivo() ? " · ativo" : " · inativo");
        u.alterar(dados.nome().trim(), dados.papel(), dados.ativo());
        auditoria.registrar(loja, Acao.ALTEROU, "usuario", id, antes,
                u.getLogin() + " · " + u.getPapel() + (u.isAtivo() ? " · ativo" : " · inativo"));
        return UsuarioDto.de(u);
    }

    /** O dono redefine a senha de quem esqueceu (a senha nunca vai para o histórico). */
    @PutMapping("/{id}/senha")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void redefinirSenha(@PathVariable String slug, @PathVariable Long id, @Valid @RequestBody NovaSenha dados) {
        Loja loja = lojas.buscar(slug);
        Usuario u = doLoja(loja, id);
        senhaDiferenteDoLogin(dados.senha(), u.getLogin());
        u.trocarSenha(senhas.encode(dados.senha()));
        auditoria.registrar(loja, Acao.ALTEROU, "usuario", id, null, u.getLogin() + " · senha redefinida pelo dono");
    }

    /** Cada um troca a própria senha (a conta principal troca no servidor). */
    @PutMapping("/eu/senha")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void trocarMinhaSenha(@PathVariable String slug, @Valid @RequestBody TrocaDeSenha dados, Authentication auth) {
        Loja loja = lojas.buscar(slug);
        if (contas.ehContaPrincipal(auth.getName())) {
            throw new Erros.Conflito("A senha da conta principal é trocada no servidor (PAINEL_SENHA). Veja o README.");
        }
        Usuario u = usuarios.findByLogin(auth.getName()).orElseThrow();
        if (!senhas.matches(dados.atual(), u.getSenhaHash())) throw new Erros.Conflito("A senha atual não confere.");
        senhaDiferenteDoLogin(dados.nova(), u.getLogin());
        u.trocarSenha(senhas.encode(dados.nova()));
        auditoria.registrar(loja, Acao.ALTEROU, "usuario", u.getId(), null, u.getLogin() + " · trocou a própria senha");
    }

    private Usuario doLoja(Loja loja, Long id) {
        return usuarios.findByIdAndLojaId(id, loja.getId()).orElseThrow(() -> new Erros.NaoEncontrado("Usuário não encontrado"));
    }

    private static void senhaDiferenteDoLogin(String senha, String login) {
        if (senha.toLowerCase(Locale.ROOT).contains(login.toLowerCase(Locale.ROOT))) {
            throw new Erros.Conflito("A senha não pode conter o login.");
        }
    }
}
