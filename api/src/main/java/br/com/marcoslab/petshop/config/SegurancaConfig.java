package br.com.marcoslab.petshop.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.time.Clock;
import java.time.ZoneId;
import java.util.List;

/**
 * Login simples do painel (HTTP Basic, sem sessão) com um usuário configurado por variável de ambiente.
 * Suficiente para uma loja; para várias lojas com vários usuários, trocar por tabela de usuários + JWT.
 */
@Configuration
public class SegurancaConfig {

    @Bean
    public SecurityFilterChain filtro(HttpSecurity http, LimiteDeLogin limiteDeLogin) throws Exception {
        http
                .addFilterBefore(limiteDeLogin, BasicAuthenticationFilter.class)
                .csrf(c -> c.disable()) // API sem cookie de sessão: CSRF não se aplica
                .cors(Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/error").permitAll()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().denyAll())
                // 401 sem o cabeçalho WWW-Authenticate, para o navegador não abrir a janela nativa de login
                .httpBasic(b -> b.authenticationEntryPoint((req, res, e) -> res.sendError(401)));
        return http.build();
    }

    /** O limite de login roda só dentro da cadeia de segurança (sem isso o Spring Boot o registraria duas vezes). */
    @Bean
    public FilterRegistrationBean<LimiteDeLogin> naoRegistrarLimiteDeLoginDeNovo(LimiteDeLogin filtro) {
        FilterRegistrationBean<LimiteDeLogin> r = new FilterRegistrationBean<>(filtro);
        r.setEnabled(false);
        return r;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    public UserDetailsService usuarios(@Value("${app.painel.usuario}") String usuario,
                                       @Value("${app.painel.senha}") String senha) {
        return new InMemoryUserDetailsManager(User.withUsername(usuario).password(senha).roles("LOJA").build());
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(@Value("${app.cors.origens}") List<String> origens) {
        CorsConfiguration c = new CorsConfiguration();
        c.setAllowedOrigins(origens);
        c.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE"));
        c.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        UrlBasedCorsConfigurationSource fonte = new UrlBasedCorsConfigurationSource();
        fonte.registerCorsConfiguration("/api/**", c);
        return fonte;
    }

    /** Relógio no fuso de Goiânia (America/Sao_Paulo); injetado para os testes poderem controlar "hoje". */
    @Bean
    public Clock clock() {
        return Clock.system(ZoneId.of("America/Sao_Paulo"));
    }
}
