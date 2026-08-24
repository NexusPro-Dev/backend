package com.factech.nexus.shared.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;

/**
 * Configuración base de seguridad.
 *
 * <p>Sin esta clase, Spring Boot autoconfigura un formulario de acceso en {@code /login}, una
 * sesión con cookie y un usuario generado. Eso contradice la decisión D-08 —token JWT sin estado de
 * sesión en servidor— y deja protegido el endpoint de salud, que el Art. XV.10 exige público.
 *
 * <p>El comportamiento por defecto es <b>denegar</b> (Art. IV.1): todo lo que no aparezca en la
 * lista de rutas públicas exige autenticación. La lista se mantiene corta a propósito, para que
 * pueda revisarse de un vistazo (security.md §6).
 *
 * <p><b>La seguridad de método queda habilitada</b> con {@code @EnableMethodSecurity}. Sin ella,
 * las anotaciones {@code @PreAuthorize} de los controladores son decorativas: el endpoint quedaría
 * accesible a cualquier autenticado y `CA-SP-008` y `CA-SP-077` no comprobarían nada. Se habilita
 * al implementarse `RF-SP-001` · `T-09`, que es el primero que declara un permiso sobre un método
 * de escritura.
 *
 * <p><b>Todavía no hay mecanismo de autenticación.</b> El inicio de sesión pertenece al módulo
 * {@code USR}, que no existe: hasta entonces, cualquier ruta no pública responde {@code 401}. Es lo
 * correcto, y no un defecto de esta configuración.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

  /** Salud del sistema: público siempre, sin detalle interno (Art. XV.10). */
  private static final String[] RUTAS_PUBLICAS = {
    "/actuator/health",
    // Los tres endpoints de sesión son públicos por definición: quien inicia
    // sesión, renueva o cierra no puede portar todavía —o ya no porta— un token
    // de acceso válido. `RF-SP-036` lo declara de forma explícita para el
    // cierre: exigir un token vigente impediría cerrar la sesión justo cuando
    // más falta hace, que es cuando se sospecha que la robaron.
    "/api/v1/auth/login",
    "/api/v1/auth/refresh",
    "/api/v1/auth/logout"
  };

  /**
   * Documentación de la API: pública solo donde se habilite de forma explícita.
   *
   * <p><b>{@code /v3/api-docs.yaml} se declara aparte y no sobra.</b> No casa con el literal exacto
   * ni con {@code /v3/api-docs/**}, que exige una barra a continuación, de modo que sin esta
   * entrada el contrato en YAML responde {@code 401} mientras el JSON responde {@code 200}. Varias
   * herramientas de generación de cliente piden el YAML por defecto, y el síntoma que ve quien lo
   * consume es «la documentación está cerrada» y no «falta un patrón» — que es la clase de fallo
   * que cuesta media tarde encontrar.
   */
  private static final String[] RUTAS_DOCUMENTACION = {
    "/v3/api-docs", "/v3/api-docs.yaml", "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**"
  };

  private final boolean documentacionPublica;
  private final JwtActorConverter actorDesdeElToken;

  public SecurityConfig(
      JwtActorConverter actorDesdeElToken,
      @Value("${nexus.security.expose-api-docs:false}") boolean documentacionPublica) {
    this.actorDesdeElToken = actorDesdeElToken;
    this.documentacionPublica = documentacionPublica;
  }

  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http
        // Sin protección CSRF: la API no usa cookies de sesión, de modo que
        // no hay credencial que el navegador adjunte de forma automática.
        .csrf(csrf -> csrf.disable())

        // Sin estado de sesión en el servidor (D-08, architecture.md §4).
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

        // Ni formulario de acceso ni autenticación básica: el acceso se
        // resolverá con el token que emita USR.
        .formLogin(form -> form.disable())
        .httpBasic(basic -> basic.disable())
        .logout(logout -> logout.disable())

        // Sin credencial válida se responde 401, no se redirige a una
        // página de acceso: esto es una API, no una aplicación web.
        .exceptionHandling(
            e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
        .authorizeHttpRequests(
            auth -> {
              auth.requestMatchers(RUTAS_PUBLICAS).permitAll();
              if (documentacionPublica) {
                auth.requestMatchers(RUTAS_DOCUMENTACION).permitAll();
              }
              auth.anyRequest().authenticated();
            })
        // El token de acceso se valida aquí, y las autoridades del actor NO
        // salen de él: las resuelve `JwtActorConverter` contra la base, para que
        // retirar un rol surta efecto de inmediato en lugar de esperar a que el
        // token expire (`security.md` §4.5).
        .oauth2ResourceServer(
            oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(actorDesdeElToken)))
        .headers(Customizer.withDefaults());

    return http.build();
  }
}
