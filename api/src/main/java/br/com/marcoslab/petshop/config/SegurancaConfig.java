package br.com.marcoslab.petshop.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
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
 * Login do painel (HTTP Basic, sem sessão): conta principal do servidor + usuários cadastrados (ver ContasDoPainel).
 * DONO vê tudo; FUNCIONARIO usa agenda, clientes, pets, vacinas e lembretes.
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
                        .requestMatchers(HttpMethod.GET, "/api/publico/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/actuator/health").permitAll()
                        // agendamento online (cliente final) e webhook do WhatsApp: sem login, com as próprias proteções
                        .requestMatchers(HttpMethod.POST, "/api/publico/lojas/*/agendamentos", "/api/publico/whatsapp/webhook",
                                "/api/publico/descadastro").permitAll()
                        // só o dono: dinheiro, campanhas, configurações, usuários, saúde e excluir cliente (LGPD)
                        .requestMatchers("/api/lojas/*/financeiro/**", "/api/lojas/*/precos/**", "/api/lojas/*/precos",
                                "/api/lojas/*/campanhas/**", "/api/lojas/*/campanhas", "/api/lojas/*/avisos").hasRole("DONO")
                        .requestMatchers(HttpMethod.PUT, "/api/lojas/*/config", "/api/lojas/*/whatsapp").hasRole("DONO")
                        .requestMatchers(HttpMethod.DELETE, "/api/lojas/*/tutores/*").hasRole("DONO")
                        .requestMatchers(HttpMethod.GET, "/api/lojas/*/usuarios/eu").authenticated()
                        .requestMatchers(HttpMethod.PUT, "/api/lojas/*/usuarios/eu/senha").authenticated()
                        .requestMatchers("/api/lojas/*/usuarios/**", "/api/lojas/*/usuarios").hasRole("DONO")
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
