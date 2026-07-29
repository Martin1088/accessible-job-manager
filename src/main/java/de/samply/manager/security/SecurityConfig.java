package de.samply.manager.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
public class SecurityConfig {
    @Autowired
    private RoleCheckSuccessHandler roleCheckSuccessHandler;
    @Autowired
    private GroupsGrantedAuthoritiesMapper groupsGrantedAuthoritiesMapper;

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .ignoringRequestMatchers("/api/public/**", "/api/**")
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/", "/index.html", "/favicon.ico", "/error",
                                "/assets/**",
                                "/*.js", "/*.css", "/*.map",
                                "/runtime*.js",
                                "/polyfills*.js",
                                "/main*.js",
                                "/styles*.css"
                        ).permitAll()
                        .requestMatchers("/login", "/oauth2/**", "/error").permitAll()
                        .requestMatchers("/impressum", "/datenschutz").permitAll()
                        .requestMatchers("/api/public/**").permitAll()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().authenticated()
                )
                .oauth2Login(oauth -> oauth
                        .userInfoEndpoint(u -> u.userAuthoritiesMapper(groupsGrantedAuthoritiesMapper))
                        .successHandler(roleCheckSuccessHandler)
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((req, res, e) -> {
                            String accept = req.getHeader("Accept");
                            boolean browserRequest = accept != null
                                    && accept.contains("text/html")
                                    && !accept.equals("application/json");
                            if (browserRequest) {
                                res.sendRedirect("/oauth2/authorization/authentik");
                            } else {
                                res.sendError(401);
                            }
                        })
                )
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/")
                        .deleteCookies("JSESSIONID")
                        .invalidateHttpSession(true));
        return http.build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration conf = new CorsConfiguration();
        conf.addAllowedOrigin("http://localhost:4200");
        conf.addAllowedMethod(HttpMethod.OPTIONS);
        conf.addAllowedMethod(HttpMethod.GET);
        conf.addAllowedMethod(HttpMethod.POST);
        conf.addAllowedMethod(HttpMethod.PUT);
        conf.addAllowedMethod(HttpMethod.DELETE);
        conf.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        conf.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", conf);
        return source;
    }
}
