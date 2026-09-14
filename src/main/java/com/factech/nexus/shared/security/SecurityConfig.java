package com.factech.nexus.shared.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
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
 * <p><b>La cadena termina con dos filtros propios</b>, los dos después de la autorización y en este
 * orden: {@link ActorCaptureFilter} apunta quién hizo la petición mientras el contexto todavía
 * existe, y {@link MustChangePasswordFilter} retiene a quien tiene el cambio obligatorio pendiente
 * (`RF-SP-034` · `FA-002`). El orden no es indiferente: al revés, la petición retenida quedaría
 * registrada como anónima.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

  /**
   * Salud del sistema: público siempre, sin detalle interno (Art. XV.10).
   *
   * <p><b>El comodín no sobra</b>: desde el issue #31 la salud son <b>tres</b> rutas —la general,
   * {@code /liveness} y {@code /readiness}—, y quien las consulta es el orquestador, que no porta
   * credencial ninguna. Sin el comodín, las dos sondas nuevas responden {@code 401} y el contenedor
   * concluye que la aplicación está enferma justo cuando está sana.
   */
  private static final String[] RUTAS_PUBLICAS = {
    "/actuator/health",
    "/actuator/health/**",
    // Los tres endpoints de sesión son públicos por definición: quien inicia
    // sesión, renueva o cierra no puede portar todavía —o ya no porta— un token
    // de acceso válido. `RF-SP-036` lo declara de forma explícita para el
    // cierre: exigir un token vigente impediría cerrar la sesión justo cuando
    // más falta hace, que es cuando se sospecha que la robaron.
    "/api/v1/auth/login",
    "/api/v1/auth/refresh",
    "/api/v1/auth/logout",
    // Y las dos de la recuperación (`RF-SP-040`), por definición: quien olvidó
    // su contraseña no puede autenticarse para pedir recuperarla. La segunda la
    // autoriza el permiso temporal que la primera envía, no un token.
    "/api/v1/auth/password-recovery",
    "/api/v1/auth/password-recovery/confirmation",
    // EL REGISTRO POR ENLACE (`RF-SP-045`, 09-09-2026), Y ES EL PRIMERO PÚBLICO
    // QUE ESCRIBE. Las demás de esta lista o leen, o consumen una credencial
    // que alguien emitió; esta CREA CUENTAS.
    //
    // Es público por definición y no por decisión: quien se registra no tiene
    // cuenta con la que autenticarse. Lo que sostiene que eso no sea un agujero
    // son dos cosas y ninguna es el token — la cuenta nace en `FTD_PENDIENTE`,
    // que autentica y NO OPERA, y `RateLimitFilter` acota el origen.
    "/api/v1/auth/registration",
    // EL HOTLINK (`RF-PM-008`), Y ES LA PRIMERA RUTA PÚBLICA POR DECISIÓN Y NO
    // POR DEFINICIÓN. Las cinco de arriba lo son porque quien las llama no
    // puede portar todavía un token; esta lo es porque UN ENLACE SE ABRE ANTES
    // DE REGISTRARSE.
    //
    // Es también la primera que publica el nombre de una persona, y por eso su
    // alcance está acotado dos veces: solo productos activos de alcance
    // `HOTLINKS` (`RN-PM-021`) y solo el nombre de quien es fuerza comercial
    // (`RN-PM-022`). Lo que no procede responde el MISMO `404` en los seis
    // casos — distinguirlos convertiría el enlace en un oráculo de existencia.
    //
    // El recorrido a ciegas lo acota `RateLimitFilter` POR ORIGEN, y queda
    // escrito que acotar no es impedir (`spec.md` §10).
    "/api/v1/hotlinks/*/*"
  };

  /**
   * Los <b>cuatro</b> catálogos que el formulario de registro necesita <b>antes</b> de que exista
   * la cuenta.
   *
   * <p><b>Van aparte de {@link #RUTAS_PUBLICAS} porque aquí el MÉTODO importa</b>, y esa es toda la
   * razón de que exista esta segunda lista. {@code /api/v1/countries} responde a tres verbos: el
   * {@code GET} que se abre y un {@code POST} y un {@code PATCH} que <b>no</b>. Metida en la lista
   * de arriba, la ruta entera quedaría en {@code permitAll} a nivel de filtro: las escrituras
   * seguirían protegidas —lo hace {@code @PreAuthorize}—, pero un anónimo pasaría de recibir {@code
   * 401} a recibir {@code 403}, y eso es decirle «existe y no puedes» en lugar de «identifícate».
   *
   * <p><b>Se abren el 08-09-2026 por decisión del responsable del proyecto</b>, y lo que resuelven
   * es un hueco que este documento llevaba tres migraciones declarando: el registro público de
   * `RF-SP-045` necesita elegir país, tipo de documento y broker, y ninguno de los tres se podía
   * leer sin haber iniciado sesión — que es justo lo que todavía no se ha hecho.
   *
   * <p><b>Lo que publican no identifica a nadie</b>: son listas de opciones. Es la diferencia con
   * el hotlink, que publica el nombre de una persona; aquí no hay oráculo posible porque no hay
   * nada que sondear.
   *
   * <p><b>Y su consecuencia se declara en lugar de disimularse</b>: {@code countries:read}, {@code
   * document-types:read} y {@code brokers:read} <b>dejan de gobernar estas lecturas</b>. Los
   * permisos siguen sembrados —retirarlos rompería los roles que ya los tengan—, y quedan como los
   * cuatro de `movements:` y `products:hotlink`: sembrados y sin endpoint que los exija.
   *
   * <p><b>EL CUARTO ENTRA EL 09-09-2026 Y ES EL DE MÉTODOS DE PAGO</b> (`RF-MV-009`, `RN-MV-024`),
   * por lo mismo que los otros tres: el mismo formulario elige <b>con qué se paga</b> antes de que
   * exista la cuenta. Se aparta de ellos en dos cosas, y las dos conviene tenerlas escritas:
   *
   * <ul>
   *   <li><b>No deja ningún permiso huérfano</b>, porque <b>nunca exigió uno</b>: no hubo
   *       {@code @PreAuthorize} que retirar. Los cuatro permisos de {@code movements:} gobiernan
   *       ventas, y ninguno gobernaba esta lectura.
   *   <li><b>Hoy esa ruta solo responde a un {@code GET}</b>, de modo que las dos listas se
   *       comportarían igual y la elección parece indiferente. No lo es: el catálogo de métodos de
   *       pago <b>está aplazado, no descartado</b> como recurso administrable (`requirements/mv.md`
   *       §5.3), y el día que tenga alta, esa escritura respondería {@code 403} en lugar de {@code
   *       401} si la ruta estuviera abierta entera. Se ata ahora, que cuesta una línea.
   * </ul>
   *
   * <p><b>Y lo que hace que abrirlo no publique nada no está aquí</b>, sino en la consulta: la
   * respuesta lleva <b>dos ejes</b> —{@code is_active} y {@code visibility = 'PUBLICO'}
   * (`RN-MV-023`)—, de modo que el anónimo recibe exactamente lo que recibía el autenticado. Con un
   * solo eje, esta línea habría puesto el método {@code GRATIS} delante de cualquiera.
   */
  private static final String[] CATALOGOS_PUBLICOS = {
    "/api/v1/countries", "/api/v1/document-types", "/api/v1/brokers", "/api/v1/payment-methods"
  };

  /**
   * Las reseñas de un producto (`RF-PM-012`, 14-09-2026): la <b>segunda ruta pública que publica el
   * nombre de una persona</b>, después del hotlink, y por eso hereda sus dos decisiones — del autor
   * solo nombre y apellido (`RN-PM-030`), y {@code 404} uniforme (`RN-PM-028`).
   *
   * <p><b>Va en la lista por método y solo en {@code GET}</b>, por lo mismo que los catálogos: la
   * misma ruta responde a un {@code POST} que exige {@code products:comment}, y abrirla entera
   * dejaría al anónimo recibiendo {@code 403} donde debe recibir {@code 401}. <b>El patrón es de UN
   * segmento</b> y termina en {@code /comments}: no alcanza a {@code /comments/mine} ni a {@code
   * /comments/{commentId}}, y {@code EndpointPermissionsIT} lo comprueba con los tres.
   *
   * <p>Es pública por <b>decisión</b> —la pantalla del hotlink las necesita y no tiene con qué
   * autenticarse— y no deja ningún permiso huérfano: nunca exigió uno.
   */
  private static final String RESENAS_PUBLICAS = "/api/v1/products/*/comments";

  /**
   * La imagen de portada de un producto (`RF-PM-016`, 14-09-2026): la única ruta del sistema que
   * sirve bytes y no JSON, y la tercera pública de `PM`.
   *
   * <p>Es pública por lo mismo que las reseñas: una de las cuatro lecturas que devuelven su
   * dirección es el hotlink, que no tiene con qué autenticarse, y un {@code <img>} no lleva
   * cabecera {@code Authorization}. <b>Solo en {@code GET}</b> y en la lista por método, no en
   * {@code RUTAS_PUBLICAS}: {@code /product-images/} no tiene hoy ninguna escritura —subir y quitar
   * la portada viven en {@code /products/{id}/cover} bajo {@code products:update}—, y el día que la
   * tenga no debe nacer abierta por un patrón demasiado ancho. Lo que la protege está en {@code
   * security.md} §6: solo tres tipos —{@code SVG} fuera—, {@code nosniff}, y la cota de tasa por la
   * familia. <b>No se solapa con {@code RESENAS_PUBLICAS}</b>: otro recurso.
   */
  private static final String PORTADAS_PUBLICAS = "/api/v1/product-images/*";

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
  private final ActorCaptureFilter actorParaElRegistro;
  private final ObjectMapper json;

  public SecurityConfig(
      JwtActorConverter actorDesdeElToken,
      ActorCaptureFilter actorParaElRegistro,
      ObjectMapper json,
      @Value("${nexus.security.expose-api-docs:false}") boolean documentacionPublica) {
    this.actorDesdeElToken = actorDesdeElToken;
    this.actorParaElRegistro = actorParaElRegistro;
    this.json = json;
    this.documentacionPublica = documentacionPublica;
  }

  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http
        // Orígenes autorizados para el navegador. La política la define
        // `CorsConfig` a partir de configuración —nunca de literales— y este
        // filtro corre ANTES de la autorización: la comprobación previa
        // (`OPTIONS`), que el navegador emite sin cabecera `Authorization`, se
        // responde aquí y no necesita figurar entre las rutas públicas.
        .cors(Customizer.withDefaults())

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
              // Solo el GET: ver `CATALOGOS_PUBLICOS`. El `POST` y el `PATCH`
              // de países siguen exigiendo token, y siguen respondiendo `401`
              // sin él en lugar de `403`.
              auth.requestMatchers(HttpMethod.GET, CATALOGOS_PUBLICOS).permitAll();
              // Solo el GET y solo esa ruta: ver `RESENAS_PUBLICAS`.
              auth.requestMatchers(HttpMethod.GET, RESENAS_PUBLICAS).permitAll();
              // Solo el GET: ver `PORTADAS_PUBLICAS`. Sirve bytes, no JSON.
              auth.requestMatchers(HttpMethod.GET, PORTADAS_PUBLICAS).permitAll();
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

        // Apunta el actor DENTRO de esta cadena, que es el único sitio donde
        // todavía existe: Spring Security limpia su contexto en su propio
        // `finally`, antes que el de cualquier filtro que la envuelva. Sin esto,
        // `request_log` registraría toda petición como anónima — con filas que
        // existen y parecen correctas, que es la peor forma de equivocarse.
        .addFilterAfter(actorParaElRegistro, AuthorizationFilter.class)

        // Retiene a quien tiene el cambio obligatorio pendiente (`RF-SP-034`
        // `FA-002`). Va DESPUÉS del filtro anterior a propósito: la petición
        // rechazada también debe quedar en `request_log` con su actor, y no
        // como anónima. Y después de la autorización, para que quien además
        // carece del permiso reciba el 403 que le corresponde por lo que
        // intentaba hacer, no uno que le cuente algo de su cuenta.
        .addFilterAfter(new MustChangePasswordFilter(json), ActorCaptureFilter.class)
        .headers(Customizer.withDefaults());

    return http.build();
  }
}
