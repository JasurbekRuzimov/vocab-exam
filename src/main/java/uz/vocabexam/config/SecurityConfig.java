package uz.vocabexam.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.*;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import uz.vocabexam.security.CustomUserDetailsService;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final CustomUserDetailsService userDetailsService;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public AuthenticationManager authenticationManager() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return new ProviderManager(provider);
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // CSRF hamma joyda yoqilgan: brauzer API ga sessiya cookie bilan murojaat qiladi,
            // token esa layout/base.html dagi fetch o'rami orqali avtomatik qo'shiladi.
            .csrf(csrf -> {})
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/login", "/css/**", "/js/**", "/images/**", "/favicon.ico").permitAll()
                .requestMatchers("/admin/centers/**").hasRole("SUPER_ADMIN")
                .requestMatchers("/admin/**", "/api/users/**").hasAnyRole("SUPER_ADMIN", "CENTER_ADMIN")
                .requestMatchers("/teacher/**",
                    "/api/word-sets/**", "/api/exams/**",
                    "/api/assignments/**", "/api/answers/**").hasAnyRole("SUPER_ADMIN", "TEACHER")
                .requestMatchers("/student/**", "/api/online-exams/my/**").hasRole("STUDENT")
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/login")
                .loginProcessingUrl("/login")
                .defaultSuccessUrl("/dashboard", true)
                // Bloklangan akkaunt yoki o'chirilgan markaz uchun aniq xabar ko'rsatamiz
                .failureHandler((req, res, ex) -> res.sendRedirect(req.getContextPath() + "/login?" +
                    (ex instanceof DisabledException ? "disabled=true" : ex instanceof LockedException ? "locked=true" : "error=true")))
                .usernameParameter("username")
                .passwordParameter("password")
                .permitAll()
            )
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/login?logout=true")
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID")
                .permitAll()
            )
            .sessionManagement(session -> session
                .maximumSessions(1)
                .expiredUrl("/login?expired=true")
            )
            .exceptionHandling(ex -> ex
                .accessDeniedPage("/access-denied")
            );

        return http.build();
    }
}