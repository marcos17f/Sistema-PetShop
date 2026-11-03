package br.com.marcoslab.petshop.usuario;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Quem pode entrar no painel: a conta principal do servidor (PAINEL_USUARIO/PAINEL_SENHA, sempre DONO, também serve de
 * recuperação se alguém esquecer a senha) e os usuários cadastrados na tela Usuários (inativos não entram).
 */
@Service
public class ContasDoPainel implements UserDetailsService {

    private final UsuarioRepository usuarios;
    private final String loginPrincipal;
    private final String senhaPrincipal;

    public ContasDoPainel(UsuarioRepository usuarios, @Value("${app.painel.usuario}") String loginPrincipal,
                          @Value("${app.painel.senha}") String senhaPrincipal) {
        this.usuarios = usuarios;
        this.loginPrincipal = loginPrincipal;
        this.senhaPrincipal = senhaPrincipal;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String login) {
        if (loginPrincipal.equals(login)) {
            return User.withUsername(login).password(senhaPrincipal).roles(Papel.DONO.name()).build();
        }
        Usuario u = usuarios.findByLogin(login).orElseThrow(() -> new UsernameNotFoundException("usuário não encontrado"));
        return User.withUsername(u.getLogin()).password(u.getSenhaHash()).roles(u.getPapel().name())
                .disabled(!u.isAtivo()).build();
    }

    boolean ehContaPrincipal(String login) {
        return loginPrincipal.equals(login);
    }
}
